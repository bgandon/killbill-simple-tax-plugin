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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSetMultimap.builder;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Ordering.natural;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.killbill.billing.ObjectType.INVOICE_ITEM;
import static org.killbill.billing.plugin.api.invoice.PluginInvoiceItem.createAdjustmentItem;
import static org.killbill.billing.plugin.api.invoice.PluginInvoiceItem.createTaxItem;
import static org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig.DEFAULT_TAX_ITEM_DESC;
import static org.killbill.billing.plugin.simpletax.internal.TaxCodeService.TAX_CODES_FIELD_NAME;
import static org.killbill.billing.plugin.simpletax.util.InvoiceHelpers.amountWithAdjustments;
import static org.killbill.billing.plugin.simpletax.util.InvoiceHelpers.sumAmounts;
import static org.osgi.service.log.LogService.LOG_ERROR;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.internal.TaxCodeService;
import org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxConfigurationHandler;
import org.killbill.billing.plugin.simpletax.resolving.NullTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.TaxResolver;
import org.killbill.billing.plugin.simpletax.util.ImmutableCustomField;
import org.killbill.billing.plugin.simpletax.util.LazyValue;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.Clock;
import org.killbill.killbill.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
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
public class SimpleTaxPlugin extends PluginInvoicePluginApi {

    private SimpleTaxConfigurationHandler configHandler;

    /**
     * Creates a new simple-tax plugin.
     *
     * @param configHandler
     *            The configuration handler to use for this plugin instance.
     * @param metaApi
     *            The Kill Bill meta-API.
     * @param configService
     *            The service to use for accessing the plugin configuration
     *            properties.
     * @param logService
     *            The service to use when logging events.
     * @param clockService
     *            The clock service to use when accessing the current time.
     */
    public SimpleTaxPlugin(SimpleTaxConfigurationHandler configHandler, OSGIKillbillAPI metaApi,
            OSGIConfigPropertiesService configService, OSGIKillbillLogService logService, Clock clockService) {
        super(metaApi, configService, logService, clockService);
        this.configHandler = configHandler;
    }

    protected OSGIKillbillAPI services() {
        return killbillAPI;
    }

    protected Clock getClockService() {
        return clock;
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
    private Multimap<UUID, InvoiceItem> allAjdustmentsGroupedByAdjustedItem(Set<Invoice> allInvoices) {
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
    private InvoiceItem buildAdjustmentForTaxItem(InvoiceItem taxItemToAdjust, LocalDate date,
            @Nullable BigDecimal adjustmentAmount, @Nonnull String description) {
        checkArgument(isTaxItem(taxItemToAdjust), "not a tax type: %s", taxItemToAdjust.getInvoiceItemType());
        if ((adjustmentAmount == null) || (ZERO.compareTo(adjustmentAmount) == 0)) {
            return null;
        }
        return createAdjustmentItem(taxItemToAdjust, taxItemToAdjust.getInvoiceId(), date, null, adjustmentAmount,
                description);
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
     * @return A new tax item or {@code null}.
     * @throws IllegalArgumentException
     *             if {@code taxableItem} is not {@linkplain #isTaxableItem of a
     *             taxable type}.
     */
    private InvoiceItem buildTaxItem(InvoiceItem taxableItem, LocalDate date, BigDecimal taxAmount,
            @Nonnull String description) {
        checkArgument(isTaxableItem(taxableItem), "not of a taxable type: %s", taxableItem.getInvoiceItemType());
        if ((taxAmount == null) || (ZERO.compareTo(taxAmount) == 0)) {
            return null;
        }
        return createTaxItem(taxableItem, taxableItem.getInvoiceId(), date, null, taxAmount, description);
    }

    /**
     * Computes the amount of tax for a given amount, in the context of a given
     * invoice item, invoice, and account.
     *
     * @param item
     *            TODO
     * @param amount
     *            the adjusted amount of the item to tax
     * @param tax
     *            TODO
     * @param cfg
     *            the applicable plugin config for the current kill bill tenant
     *
     * @return the amount of tax that should be paid by the account
     */
    protected BigDecimal computeTaxAmount(InvoiceItem item, BigDecimal amount, @Nullable TaxCode tax,
            SimpleTaxConfig cfg) {
        if (tax == null) {
            return ZERO;
        }
        return amount.multiply(tax.getRate()).setScale(cfg.getTaxAmountPrecision(), HALF_UP);
    }

    /**
     * Returns additional invoice items to be added to the invoice upon
     * creation. These can be new tax items, or adjustments on existing tax
     * items.
     * <p>
     * The first goal here is to list missing tax items in the passed invoice
     * that is being created.
     * <p>
     * Then, adjustments might have been created on any historical invoice of
     * the account. Thus, this method also lists necessary adjustments to any
     * tax items in any historical invoices.
     * <p>
     * But no new tax item is listed for historical invoices. If a taxable item
     * has not been taxed at the time, we estimated here that there might be a
     * good reason for that.
     *
     * @param newInvoice
     *            The invoice that is being created.
     * @param properties
     *            Undocumented. No clue about what kind of plugin properties
     *            this is supposed to provide. Don't use this until it is
     *            properly documented. Currently in Kill Bill version 0.15.6,
     *            this is always an immutable empty list.
     * @param context
     *            The call context.
     * @return The list of new tax items (on the invoice being created), or
     *         adjustments on existing tax items (on any historical invoice of
     *         the same account).
     */
    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(Invoice newInvoice, boolean dryRun,
            Iterable<PluginProperty> properties, CallContext context) {

        TaxComputationContext taxCtx = createTaxComputationContext(newInvoice, context);
        TaxResolver taxResolver = instanciateTaxResolver(taxCtx);
        Map<UUID, TaxCode> newTaxCodes = addMissingTaxCodes(newInvoice, taxResolver, taxCtx, context);

        List<InvoiceItem> additionalItems = new LinkedList<InvoiceItem>();
        for (Invoice invoice : taxCtx.getAllInvoices()) {

            List<InvoiceItem> newItems;
            if (invoice.equals(newInvoice)) {
                newItems = computeTaxOrAdjustmentItemsForNewInvoice(invoice, taxCtx, newTaxCodes);
            } else {
                newItems = computeAdjustmentItemsForHistoricalInvoice(invoice, taxCtx);
            }
            additionalItems.addAll(newItems);
        }
        return additionalItems;
    }

    /**
     * Pre-compute data that will be useful to computing tax items and tax
     * adjustment items.
     *
     * @param newInvoice
     *            The newly created invoice to add tax to or adjust tax items
     *            of.
     * @param context
     *            The call context.
     * @return Helpful pre-computed data for adding or adjusting taxes in the
     *         account invoices.
     */
    private TaxComputationContext createTaxComputationContext(Invoice newInvoice, TenantContext context) {

        SimpleTaxConfig cfg = configHandler.getConfigurable(context.getTenantId());

        Account account = getAccount(newInvoice.getAccountId(), context);

        Set<Invoice> allInvoices = allInvoicesOfAccount(account, newInvoice, context);

        Function<InvoiceItem, BigDecimal> toAdjustedAmount = toAdjustedAmount(allInvoices);
        Ordering<InvoiceItem> byAdjustedAmount = natural().onResultOf(toAdjustedAmount);

        TaxCodeService taxCodeService = taxCodeService(account, allInvoices, cfg, context);

        return new TaxComputationContext(cfg, account, allInvoices, toAdjustedAmount, byAdjustedAmount, taxCodeService);
    }

    private TaxResolver instanciateTaxResolver(TaxComputationContext taxCtx) {
        Constructor<? extends TaxResolver> constructor = taxCtx.getConfig().getTaxResolverConstructor();
        Exception exc;
        try {
            return constructor.newInstance(taxCtx);
        } catch (InstantiationException e) {
            exc = e;
        } catch (IllegalAccessException e) {
            exc = e;
        } catch (IllegalArgumentException e) {
            exc = e;
        } catch (InvocationTargetException e) {
            exc = e;
        }
        logService.log(LOG_ERROR, "Cannot instanciate tax resolver. Defaulting to [" + NullTaxResolver.class.getName()
                + "].", exc);
        return new NullTaxResolver(taxCtx);
    }

    /**
     * Lists all invoice of account as {@linkplain ImmutableSet immutable set},
     * including the passed {@code newInvoice} that is the new invoice being
     * currently created.
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
    private Set<Invoice> allInvoicesOfAccount(Account account, Invoice newInvoice, TenantContext context) {
        ImmutableSet.Builder<Invoice> builder = ImmutableSet.builder();
        builder.addAll(getInvoicesByAccountId(account.getId(), context));

        // Workaround for https://github.com/killbill/killbill/issues/265
        builder.add(newInvoice);

        return builder.build();
    }

    /**
     * Creates the {@linkplain Function function} that returns the adjusted
     * amount out of a given {@linkplain InvoiceItem invoice item}.
     *
     * @param allInvoices
     *            the collection of all invoices for a given account
     * @return the function that returns the adjusted amount of an invoice item.
     */
    private Function<InvoiceItem, BigDecimal> toAdjustedAmount(Set<Invoice> allInvoices) {
        final Multimap<UUID, InvoiceItem> allAdjustments = allAjdustmentsGroupedByAdjustedItem(allInvoices);

        return new Function<InvoiceItem, BigDecimal>() {
            @Override
            public BigDecimal apply(InvoiceItem item) {
                return amountWithAdjustments(item, allAdjustments);
            }
        };
    }

    private TaxCodeService taxCodeService(Account account, Set<Invoice> allInvoices, SimpleTaxConfig cfg,
            final TenantContext context) {
        LazyValue<StaticCatalog, CatalogApiException> catalog = new LazyValue<StaticCatalog, CatalogApiException>() {
            @Override
            protected StaticCatalog initialize() throws CatalogApiException {
                return services().getCatalogUserApi().getCurrentCatalog(null, context);
            }
        };
        SetMultimap<UUID, CustomField> taxFieldsOfAllInvoices = taxFieldsOfInvoices(account, allInvoices, context);
        return new TaxCodeService(catalog, cfg, taxFieldsOfAllInvoices);
    }

    private SetMultimap<UUID, CustomField> taxFieldsOfInvoices(Account account, Set<Invoice> allInvoices,
            TenantContext context) {
        CustomFieldUserApi customFieldsService = services().getCustomFieldUserApi();
        List<CustomField> allCustomFields = customFieldsService.getCustomFieldsForAccountType(account.getId(),
                INVOICE_ITEM, context);
        if ((allCustomFields == null) || allCustomFields.isEmpty()) {
            return ImmutableSetMultimap.of();
        }

        Map<UUID, Invoice> invoiceOfItem = newHashMap();
        for (Invoice invoice : allInvoices) {
            for (InvoiceItem item : invoice.getInvoiceItems()) {
                invoiceOfItem.put(item.getId(), invoice);
            }
        }

        ImmutableSetMultimap.Builder<UUID, CustomField> taxFieldsOfInvoice = ImmutableSetMultimap.builder();
        for (CustomField field : allCustomFields) {
            if (TAX_CODES_FIELD_NAME.equals(field.getFieldName())) {
                Invoice invoice = invoiceOfItem.get(field.getObjectId());
                taxFieldsOfInvoice.put(invoice.getId(), field);
            }
        }
        return taxFieldsOfInvoice.build();
    }

    /**
     * Add missing tax codes.
     *
     * @param newInvoice
     * @param resolver
     *            TODO
     * @param taxCtx
     */
    private Map<UUID, TaxCode> addMissingTaxCodes(Invoice newInvoice, TaxResolver resolver,
            TaxComputationContext taxCtx, CallContext callCtx) {
        // Obtain tax codes from products of invoice items
        TaxCodeService taxCodesService = taxCtx.getTaxCodeService();
        SetMultimap<UUID, TaxCode> configuredTaxCodesForInvoiceItems = taxCodesService
                .resolveTaxCodesFromConfig(newInvoice);

        SetMultimap<UUID, TaxCode> existingTaxCodesForInvoiceItems = taxCodesService.findExistingTaxCodes(newInvoice);

        ImmutableMap.Builder<UUID, TaxCode> newTaxCodes = ImmutableMap.builder();
        // Add product tax codes to custom field if null or empty
        for (InvoiceItem item : newInvoice.getInvoiceItems()) {
            if (!isTaxableItem(item)) {
                continue;
            }
            Set<TaxCode> expectedTaxCodes = configuredTaxCodesForInvoiceItems.get(item.getId());
            if ((expectedTaxCodes == null) || expectedTaxCodes.isEmpty()) {
                continue;
            }
            Set<TaxCode> existingTaxCodes = existingTaxCodesForInvoiceItems.get(item.getId());
            if (existingTaxCodes != null) {
                // Don't override existing tax codes
                continue;
            }

            // resolve tax codes using regulation-specific logic
            TaxCode applicableCode = resolver.applicableCodeForItem(expectedTaxCodes, item);

            CustomFieldUserApi customFieldsService = services().getCustomFieldUserApi();
            ImmutableCustomField.Builder taxCodesField = ImmutableCustomField.builder()//
                    .withFieldName(TAX_CODES_FIELD_NAME)//
                    .withFieldValue(applicableCode.getName())//
                    .withObjectType(INVOICE_ITEM)//
                    .withObjectId(item.getId());
            CustomField field = taxCodesField.build();
            try {
                customFieldsService.addCustomFields(newArrayList(field), callCtx);
            } catch (CustomFieldApiException exc) {
                logService.log(LOG_ERROR,
                        "Cannot add custom field [" + field.getFieldName() + "] with value [" + field.getFieldValue()
                                + "] to invoice item [" + item.getId() + "] of invoice [" + newInvoice.getId() + "]",
                        exc);
            }
            newTaxCodes.put(item.getId(), applicableCode);
        }
        return newTaxCodes.build();
    }

    /**
     * Compute adjustment items on existing tax items in a <em>historical</em>
     * invoice.
     * <p>
     * In historical invoices, when a taxable item has no tax item, we are not
     * allowed to add any tax to it.
     * <p>
     * All we can do is adjust already taxed items. This happens when taxed
     * items have been adjusted. Then their related tax item also need being
     * adjusted.
     *
     * @param invoice
     *            A historical invoice.
     * @param ctx
     *            Some pre-computed data that will help.
     * @return A list of new adjustment items to add to the invoice. Never
     *         {@code null}.
     */
    private List<InvoiceItem> computeAdjustmentItemsForHistoricalInvoice(Invoice invoice, TaxComputationContext ctx) {

        SetMultimap<UUID, InvoiceItem> currentTaxItems = taxItemsGroupedByRelatedTaxedItems(invoice);

        SetMultimap<UUID, TaxCode> existingTaxCodes = ctx.getTaxCodeService().findExistingTaxCodes(invoice);

        List<InvoiceItem> newItems = new LinkedList<InvoiceItem>();
        for (InvoiceItem item : invoice.getInvoiceItems()) {
            if (!isTaxableItem(item)) {
                continue;
            }

            Set<InvoiceItem> relatedTaxItems = currentTaxItems.get(item.getId());
            if (relatedTaxItems.size() <= 0) {
                // When item has never been taxed in an *historical* invoice,
                // we cannot take the responsibility here for adding any tax to
                // it as an afterthought. So we just don't do anything.
                continue;
            }

            TaxCode tax = null;
            Set<TaxCode> taxes = existingTaxCodes.get(item.getId());
            if ((taxes != null) && !taxes.isEmpty()) {
                tax = taxes.iterator().next();
            }

            BigDecimal adjustedAmount = ctx.toAdjustedAmount().apply(item);
            BigDecimal expectedTaxAmount = computeTaxAmount(item, adjustedAmount, tax, ctx.getConfig());
            BigDecimal currentTaxAmount = sumAmounts(transform(relatedTaxItems, ctx.toAdjustedAmount()));

            if (currentTaxAmount.compareTo(expectedTaxAmount) != 0) {
                BigDecimal adjustmentAmount = expectedTaxAmount.subtract(currentTaxAmount);
                InvoiceItem largestTaxItem = ctx.byAdjustedAmount().max(relatedTaxItems);

                InvoiceItem adjItem = buildAdjustmentForTaxItem(largestTaxItem, invoice.getInvoiceDate(),
                        adjustmentAmount, tax.getTaxItemDescription());
                newItems.add(adjItem);
            }
        }
        return newItems;
    }

    /**
     * Compute tax items against taxable items, in a <em>newly created</em>
     * invoice, or adjust existing tax items that don't match the expected tax
     * amount, taking any adjustments into consideration.
     *
     * @param newInvoice
     *            A newly created invoice.
     * @param ctx
     *            Some pre-computed data that will help.
     * @param newTaxCodes
     *            TODO
     * @return A list of new tax items, or new adjustment items to add to the
     *         invoice. Never {@code null}.
     */
    private List<InvoiceItem> computeTaxOrAdjustmentItemsForNewInvoice(Invoice newInvoice, TaxComputationContext ctx,
            Map<UUID, TaxCode> newTaxCodes) {

        SetMultimap<UUID, InvoiceItem> currentTaxItems = taxItemsGroupedByRelatedTaxedItems(newInvoice);

        SetMultimap<UUID, TaxCode> existingTaxCodes = ctx.getTaxCodeService().findExistingTaxCodes(newInvoice);

        List<InvoiceItem> newItems = new LinkedList<InvoiceItem>();
        for (InvoiceItem item : newInvoice.getInvoiceItems()) {
            if (!isTaxableItem(item)) {
                continue;
            }

            TaxCode tax = newTaxCodes.get(item);
            if (tax == null) {
                Set<TaxCode> taxes = existingTaxCodes.get(item.getId());
                if ((taxes != null) && !taxes.isEmpty()) {
                    tax = taxes.iterator().next();
                }
            }

            BigDecimal adjustedAmount = ctx.toAdjustedAmount().apply(item);
            BigDecimal expectedTaxAmount = computeTaxAmount(item, adjustedAmount, tax, ctx.getConfig());

            Set<InvoiceItem> relatedTaxItems = currentTaxItems.get(item.getId());
            BigDecimal currentTaxAmount = sumAmounts(transform(relatedTaxItems, ctx.toAdjustedAmount()));

            String taxItemDescription = tax == null ? DEFAULT_TAX_ITEM_DESC : tax.getTaxItemDescription();

            if (currentTaxAmount.compareTo(expectedTaxAmount) < 0) {
                BigDecimal missingTaxAmount = expectedTaxAmount.subtract(currentTaxAmount);
                if (relatedTaxItems.size() <= 0) {
                    // In case a taxable item has never been taxed yet, we are
                    // allowed to add tax to it since it belongs to a newly
                    // created invoice.
                    InvoiceItem newTaxItem = buildTaxItem(item, newInvoice.getInvoiceDate(), missingTaxAmount,
                            taxItemDescription);
                    newItems.add(newTaxItem);
                } else {
                    // Here we know that 'relatedTaxItems' is not empty so we
                    // have some tax items and thus 'largestTaxItem' not to be
                    // null.
                    InvoiceItem largestTaxItem = ctx.byAdjustedAmount().max(relatedTaxItems);

                    InvoiceItem positiveAdjItem = buildAdjustmentForTaxItem(largestTaxItem,
                            newInvoice.getInvoiceDate(), missingTaxAmount, taxItemDescription);
                    newItems.add(positiveAdjItem);
                }
            } else if (currentTaxAmount.compareTo(expectedTaxAmount) > 0) {
                BigDecimal negativeAdjAmount = expectedTaxAmount.subtract(currentTaxAmount);

                // Here 'currentTaxAmount' should be > 0 (if 'expectedTaxAmount'
                // properly is > 0), so we expect the item to have some tax
                // items and thus 'largestTaxItem' not to be null.
                InvoiceItem largestTaxItem = ctx.byAdjustedAmount().max(relatedTaxItems);

                InvoiceItem negativeAdjItem = buildAdjustmentForTaxItem(largestTaxItem, newInvoice.getInvoiceDate(),
                        negativeAdjAmount, taxItemDescription);
                newItems.add(negativeAdjItem);
            }
        }
        return newItems;
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
    private SetMultimap<UUID, InvoiceItem> taxItemsGroupedByRelatedTaxedItems(Invoice invoice) {
        ImmutableSetMultimap.Builder<UUID, InvoiceItem> currentTaxItemsBuilder = builder();
        for (InvoiceItem item : invoice.getInvoiceItems()) {
            if (isTaxItem(item)) {
                currentTaxItemsBuilder.put(item.getLinkedItemId(), item);
            }
        }
        return currentTaxItemsBuilder.build();
    }
}
