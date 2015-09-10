/*
 * Copyright 2015 Benjamin Gandon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.killbill.billing.plugin.simpletax;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSetMultimap.builder;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Ordering.natural;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.killbill.billing.plugin.api.invoice.PluginInvoiceItem.createAdjustmentItem;
import static org.killbill.billing.plugin.api.invoice.PluginInvoiceItem.createTaxItem;
import static org.killbill.billing.plugin.simpletax.InvoiceHelpers.amountWithAdjustments;
import static org.killbill.billing.plugin.simpletax.InvoiceHelpers.sumAmounts;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;
import org.killbill.killbill.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;

/**
 * The Kill Bill Simple Tax Plugin main class.
 * <p>
 * This plugin adds tax items to invoices at their creation time. Actually, any
 * missing tax items are created. This applies to both the current invoice being
 * created <em>and</em> any historical invoices that would lack some tax for any
 * reason. Missing tax items are added to the invoices that their related
 * taxable item belongs to. And they get the date of the invoice they are added
 * to.
 * <p>
 * When adjustments have arisen for old invoice items, the plugin goes through
 * all invoices and finds the tax items that need corresponding adjustments to
 * conform to the expected tax amounts. These adjustments are added to the
 * invoices that their related tax items belongs to. But they get the date of
 * the new invoice being created.
 * <p>
 * The implementation is idempotent. Subsequent calls with the same inputs and
 * server state will results in no new item being created.
 * <p>
 * More complex taxation system can be supported by subclasses that override the
 * {@link #computeTaxAmount} method.
 *
 * @author Benjamin Gandon
 */
public class SimpleTaxInvoicePluginApi extends PluginInvoicePluginApi {

    private static final int DEFAULT_SCALE = 2;
    private static final String DEFAULT_TAX_ITEM_DESCRIPTION = "Tax";
    private static final String DEFAULT_TAX_RATE = "0.20";
    private static final String TAX_ITEM_DESCRIPTION = "VAT";

    public SimpleTaxInvoicePluginApi(final OSGIKillbillAPI killbillAPI,
            final OSGIConfigPropertiesService configProperties, final OSGIKillbillLogService logService,
            final Clock clock) {
        super(killbillAPI, configProperties, logService, clock);
    }

    /**
     * Groups the {@linkplain #isAdjustmentItem adjustment items} (found in a
     * given set of invoices) by the identifier of their
     * {@linkplain InvoiceItem#getLinkedItemId related} “adjusted” items.
     * <p>
     * The resulting collection is typically computed on all invoices of a given
     * account.
     *
     * @param allInvoices
     *            a list of invoices
     * @return the adjustment items, grouped by the items they relate to.
     */
    private Multimap<UUID, InvoiceItem> allAjdustmentsGroupedByAdjustedItem(final Set<Invoice> allInvoices) {
        ImmutableSetMultimap.Builder<UUID, InvoiceItem> builder = builder();
        for (Invoice invoice : allInvoices) {
            for (InvoiceItem item : invoice.getInvoiceItems()) {
                if (isAdjustmentItem(item)) {
                    builder.put(item.getLinkedItemId(), item);
                }
            }
        }
        return builder.build();
    }

    /**
     * Creates an adjustment for a tax item, or returns {@code null} if the
     * amount is {@code null} or zero.
     * <p>
     * The created adjustment item will be created in the same invoice as the
     * taxable item it relates to. But its date should be the one of the new
     * invoice, the creation of which has triggered the adjustment.
     * <p>
     * If a {@code null} description is passed, then a default description is
     * used instead.
     *
     * @param taxItemToAdjust
     *            the tax item to be adjusted, which <em>must</em> be
     *            {@linkplain #isTaxItem of a tax type}.
     * @param date
     *            the date at which the tax item above is adjusted. This
     *            typically is the date of the new invoice, the creation of
     *            which has triggered the adjustment.
     * @param adjustmentAmount
     *            the (optional) adjustment amount
     * @param description
     *            an optional description for the adjustment item to create, or
     *            {@code null} if a default description is fine
     *
     * @return an adjustment item, or {@code null}
     * @throws IllegalArgumentException
     *             if {@code taxItemToAdjust} is not {@linkplain #isTaxItem of a
     *             tax type}.
     */
    private InvoiceItem buildAdjustmentForTaxItem(final InvoiceItem taxItemToAdjust, final LocalDate date,
            @Nullable final BigDecimal adjustmentAmount, @Nullable final String description) {
        checkArgument(isTaxItem(taxItemToAdjust), "not a tax type: %s", taxItemToAdjust.getInvoiceItemType());
        if ((adjustmentAmount == null) || (ZERO.compareTo(adjustmentAmount) == 0)) {
            return null;
        }
        return createAdjustmentItem(taxItemToAdjust, taxItemToAdjust.getInvoiceId(), date, null, adjustmentAmount,
                firstNonNull(description, DEFAULT_TAX_ITEM_DESCRIPTION));
    }

    /**
     * Creates a tax item for a given taxable item, or returns {@code null} if
     * the amount of tax is {@code null} or zero.
     * <p>
     * The created tax item will be created in the same invoice as the taxable
     * item it relates to. And its date should be the one of the invoice that
     * the taxable item belongs to.
     * <p>
     * If a {@code null} description is passed, then a default description is
     * used instead.
     *
     * @param taxableItem
     *            the taxable item to tax, which <em>must</em> be
     *            {@linkplain #isTaxableItem of a taxable type}.
     * @param date
     *            the date at which the taxable item above was invoiced. This
     *            typically is the date of the invoice that the taxable item
     *            belongs to.
     * @param taxAmount
     *            the amount (of tax) for the new tax item to create
     * @param description
     *            an optional description for the tax item to create, or
     *            {@code null} if a default description is fine
     *
     * @return
     * @throws IllegalArgumentException
     *             if {@code taxableItem} is not {@linkplain #isTaxableItem of a
     *             taxable type}.
     */
    private InvoiceItem buildTaxItem(final InvoiceItem taxableItem, final LocalDate date, final BigDecimal taxAmount,
            @Nullable final String description) {
        checkArgument(isTaxableItem(taxableItem), "not of a taxable type: %s", taxableItem.getInvoiceItemType());
        if ((taxAmount == null) || (ZERO.compareTo(taxAmount) == 0)) {
            return null;
        }
        return createTaxItem(taxableItem, taxableItem.getInvoiceId(), date, null, taxAmount,
                firstNonNull(description, DEFAULT_TAX_ITEM_DESCRIPTION));
    }

    /**
     * Computes the amount of tax for a given amount, in the context of a given
     * invoice item, invoice, and account.
     * <p>
     * Subclasses might be interested in overriding this method, in order to
     * support more complex taxation systems.
     *
     * @param account
     *            the account to tax
     * @param invoice
     *            the invoice of the item
     * @param item
     *            the item to tax
     * @param amount
     *            the adjusted amount of the item to tax
     * @return the amount of tax that should be paid by the account
     */
    protected BigDecimal computeTaxAmount(final Account account, final Invoice invoice, final InvoiceItem item,
            final BigDecimal amount) {
        return amount.multiply(new BigDecimal(DEFAULT_TAX_RATE)).setScale(DEFAULT_SCALE, HALF_UP);
    }

    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice newInvoice,
            final Iterable<PluginProperty> properties, final CallContext context) {

        Account account = getAccount(newInvoice.getAccountId(), context);
        Set<Invoice> allInvoices = listAllInvoicesHistoryOfAccount(newInvoice, context);

        Function<InvoiceItem, BigDecimal> toAdjustedAmount = toAdjustedAmount(allInvoices);
        Ordering<InvoiceItem> byAdjustedAmount = natural().onResultOf(toAdjustedAmount);

        List<InvoiceItem> additionalTaxItems = new LinkedList<InvoiceItem>();
        for (Invoice invoice : allInvoices) {

            SetMultimap<UUID, InvoiceItem> currentTaxItems = taxItemsGroupedByRelatedTaxedItems(invoice);

            for (InvoiceItem item : invoice.getInvoiceItems()) {
                if (!isTaxableItem(item)) {
                    continue;
                }
                BigDecimal adjustedAmount = toAdjustedAmount.apply(item);
                BigDecimal expectedTaxAmount = computeTaxAmount(account, invoice, item, adjustedAmount);

                Set<InvoiceItem> relatedTaxItems = currentTaxItems.get(item.getId());
                BigDecimal currentTaxAmount = sumAmounts(transform(relatedTaxItems, toAdjustedAmount));

                InvoiceItem newTaxItem = null;
                if (currentTaxAmount.compareTo(expectedTaxAmount) < 0) {
                    BigDecimal missingTaxAmount = expectedTaxAmount.subtract(currentTaxAmount);
                    newTaxItem = buildTaxItem(item, invoice.getInvoiceDate(), missingTaxAmount, TAX_ITEM_DESCRIPTION);
                } else if (currentTaxAmount.compareTo(expectedTaxAmount) > 0) {
                    BigDecimal adjustmentAmount = expectedTaxAmount.subtract(currentTaxAmount);
                    final Collection<InvoiceItem> taxItems = relatedTaxItems;
                    InvoiceItem largestTaxItem = byAdjustedAmount.max(taxItems);
                    // Here 'currentTaxAmount' should be > 0 (if
                    // 'expectedTaxAmount' properly is > 0), so we expect the
                    // item to have some VAT tax items and thus
                    // 'largestTaxItem' not to be null.
                    newTaxItem = buildAdjustmentForTaxItem(largestTaxItem, newInvoice.getInvoiceDate(),
                            adjustmentAmount, TAX_ITEM_DESCRIPTION);
                }

                if (newTaxItem != null) {
                    additionalTaxItems.add(newTaxItem);
                }
            }
        }
        return additionalTaxItems;
    }

    /**
     * Lists all invoice of account as {@linkplain ImmutableSet immutable set},
     * including the one that is being created.
     * <p>
     * This implementation is indifferent to the persistence status of the
     * passed {@code newInvoice}. Persisted and not persisted invoices are
     * supported. This is a consequence of the workaround implemented to solve
     * the <a href="https://github.com/killbill/killbill/issues/265">issue
     * #265</a>.
     *
     * @param newInvoice
     *            a new invoice that is being created, which might have already
     *            been saved or not.
     * @param context
     *            the context of the call
     * @return the set of all invoices for the account, including the new one to
     *         build.
     */
    private Set<Invoice> listAllInvoicesHistoryOfAccount(final Invoice newInvoice, final CallContext context) {
        ImmutableSet.Builder<Invoice> builder = ImmutableSet.builder();
        builder.addAll(getInvoicesByAccountId(newInvoice.getAccountId(), context));

        // Workaround for https://github.com/killbill/killbill/issues/265
        builder.add(newInvoice);

        return builder.build();
    }

    /**
     * Groups the {@linkplain #isTaxItem tax items} (found in a given invoice)
     * by the identifier of their {@linkplain InvoiceItem#getLinkedItemId
     * related} “taxable” items.
     *
     * @param invoice
     *            some invoice
     * @return the tax items of the invoice, grouped by the identifier of their
     *         related (taxed) item. Never {@code null}.
     */
    private SetMultimap<UUID, InvoiceItem> taxItemsGroupedByRelatedTaxedItems(final Invoice invoice) {
        ImmutableSetMultimap.Builder<UUID, InvoiceItem> currentTaxItemsBuilder = builder();
        for (InvoiceItem item : invoice.getInvoiceItems()) {
            if (isTaxItem(item)) {
                currentTaxItemsBuilder.put(item.getLinkedItemId(), item);
            }
        }
        SetMultimap<UUID, InvoiceItem> currentTaxItems = currentTaxItemsBuilder.build();
        return currentTaxItems;
    }

    /**
     * Creates the {@linkplain Function function} that returns the adjusted
     * amount out of a given {@linkplain InvoiceItem invoice item}.
     *
     * @param allInvoices
     *            the collection of all invoices for a given account
     * @return the function that returns the adjusted amount of an invoice item.
     */
    private Function<InvoiceItem, BigDecimal> toAdjustedAmount(final Set<Invoice> allInvoices) {
        final Multimap<UUID, InvoiceItem> allAdjustments = allAjdustmentsGroupedByAdjustedItem(allInvoices);
        Function<InvoiceItem, BigDecimal> toAdjustedAmount = new Function<InvoiceItem, BigDecimal>() {
            @Override
            public BigDecimal apply(final InvoiceItem item) {
                return amountWithAdjustments(item, allAdjustments);
            }
        };
        return toAdjustedAmount;
    }
}
