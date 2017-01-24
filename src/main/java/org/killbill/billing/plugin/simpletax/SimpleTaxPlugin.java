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
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Ordering.natural;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.killbill.billing.ObjectType.INVOICE;
import static org.killbill.billing.ObjectType.INVOICE_ITEM;
import static org.killbill.billing.notification.plugin.api.ExtBusEventType.INVOICE_CREATION;
import static org.killbill.billing.plugin.api.invoice.PluginInvoiceItem.createAdjustmentItem;
import static org.killbill.billing.plugin.api.invoice.PluginInvoiceItem.createTaxItem;
import static org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig.DEFAULT_TAX_ITEM_DESC;
import static org.killbill.billing.plugin.simpletax.config.http.CustomFieldService.TAX_COUNTRY_CUSTOM_FIELD_NAME;
import static org.killbill.billing.plugin.simpletax.internal.TaxCodeService.TAX_CODES_FIELD_NAME;
import static org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxActivator.PLUGIN_NAME;
import static org.killbill.billing.plugin.simpletax.util.InvoiceHelpers.amountWithAdjustments;
import static org.killbill.billing.plugin.simpletax.util.InvoiceHelpers.sumAmounts;
import static org.osgi.service.log.LogService.LOG_DEBUG;
import static org.osgi.service.log.LogService.LOG_ERROR;
import static org.osgi.service.log.LogService.LOG_INFO;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.notification.plugin.api.ExtBusEvent;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.plugin.api.PluginTenantContext;
import org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.config.http.CustomFieldService;
import org.killbill.billing.plugin.simpletax.internal.Country;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.internal.TaxCodeService;
import org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxConfigurationHandler;
import org.killbill.billing.plugin.simpletax.resolving.NullTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.TaxResolver;
import org.killbill.billing.plugin.simpletax.util.CheckedLazyValue;
import org.killbill.billing.plugin.simpletax.util.CheckedSupplier;
import org.killbill.billing.plugin.simpletax.util.ImmutableCustomField;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.Clock;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillLogService;
import org.killbill.billing.osgi.libs.killbill.OSGIServiceNotAvailable;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;

/**
 * Main class for the Kill Bill Simple Tax Plugin.
 * <p>
 * This plugin adds tax items to each new invoice that is being created, based
 * on the tax codes that {@linkplain SimpleTaxConfig have been configured} and
 * then {@linkplain TaxResolver resolved}.
 * <p>
 * Tax codes that are directly set on invoice items, before this plugin run,
 * take precedence and won't be overridden.
 * <p>
 * Tax codes can also be added, removed or changed on historical invoices. The
 * plugin adds missing taxes or adjusts existing tax items accordingly.
 * <p>
 * Country-specific rules can be implemented by configuring custom
 * {@link TaxResolver} implementations.
 * <p>
 * The implementation is idempotent. Subsequent calls with the same inputs and
 * server state will results in no new item being created.
 *
 * @author Benjamin Gandon
 * @see SimpleTaxConfig
 * @see TaxResolver
 */
public class SimpleTaxPlugin extends PluginInvoicePluginApi implements OSGIKillbillEventHandler {

    private SimpleTaxConfigurationHandler configHandler;
    private CustomFieldService customFieldService;

    /**
     * Creates a new simple-tax plugin.
     *
     * @param configHandler
     *            The configuration handler to use for this plugin instance.
     * @param customFieldService
     *            The service to use when accessing custom fields.
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
    public SimpleTaxPlugin(SimpleTaxConfigurationHandler configHandler, CustomFieldService customFieldService,
            OSGIKillbillAPI metaApi, OSGIConfigPropertiesService configService, OSGIKillbillLogService logService,
            Clock clockService) {
        super(metaApi, configService, logService, clockService);
        this.configHandler = configHandler;
        this.customFieldService = customFieldService;
    }

    /**
     * @return The Kill Bill services that constitute the API.
     */
    protected OSGIKillbillAPI services() {
        return killbillAPI;
    }

    /**
     * Returns additional invoice items to be added to the invoice upon
     * creation, based on the tax codes that have been configured, or directly
     * set on invoice items.
     * <p>
     * This method produces two types of additional invoice items.
     * <p>
     * First, this method lists any missing tax items in the new invoice.
     * <p>
     * Then, adjustments might have been created on any historical invoice of
     * the account. Thus, this method also lists any necessary adjustments to
     * any tax items in any historical invoices.
     * <p>
     * Plus, tax codes can be added, changed or removed on historical invoices.
     * The affected tax amounts will be adjusted accordingly.
     *
     * @param newInvoice
     *            The invoice that is being created.
     * @param properties
     *            Any user-specified plugin properties, coming straight out of
     *            the API request that has triggered this code to run. See their
     *            <a href=
     *            "http://docs.killbill.io/0.15/userguide_payment.html#_plugin_properties"
     *            >documentation for payment plugins</a>.
     * @param callCtx
     *            The context in which this code is running.
     * @return A new immutable list of new tax items, or adjustments on existing
     *         tax items. Never {@code null}, and guaranteed not having any
     *         {@code null} elements.
     */
    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(Invoice newInvoice, boolean dryRun, Iterable<PluginProperty> properties,
            CallContext callCtx) {

        TaxComputationContext taxCtx = createTaxComputationContext(newInvoice, callCtx);
        TaxResolver taxResolver = instanciateTaxResolver(taxCtx);
        Map<UUID, TaxCode> newTaxCodes = addMissingTaxCodes(newInvoice, taxResolver, taxCtx, callCtx);

        ImmutableList.Builder<InvoiceItem> additionalItems = ImmutableList.builder();
        for (Invoice invoice : taxCtx.getAllInvoices()) {

            List<InvoiceItem> newItems;
            if (invoice.equals(newInvoice)) {
                newItems = computeTaxOrAdjustmentItemsForNewInvoice(invoice, taxCtx, newTaxCodes);
            } else {
                newItems = computeTaxOrAdjustmentItemsForHistoricalInvoice(invoice, taxCtx);
            }
            additionalItems.addAll(newItems);
        }
        return additionalItems.build();
    }

    @Override
    public void handleKillbillEvent(ExtBusEvent event) {
        logService.log(LOG_DEBUG, "Received event [" + event.getEventType() + "] for object [" + event.getObjectId()
                + "] of type [" + event.getObjectType() + "] belonging to account [" + event.getAccountId()
                + "] in tenant [" + event.getTenantId() + "]");

        if (!INVOICE_CREATION.equals(event.getEventType())) {
            return;
        }
        if (!INVOICE.equals(event.getObjectType())) {
            return;
        }
        UUID invoiceId = event.getObjectId();
        UUID tenantId = event.getTenantId();
        logService.log(LOG_INFO, "Adding tax codes to invoice [" + invoiceId
                + "] as post-creation treatment for tenant [" + tenantId + "]");

        Invoice newInvoice;
        try {
            newInvoice = getInvoiceUserApi().getInvoice(invoiceId, new PluginTenantContext(tenantId));
        } catch (OSGIServiceNotAvailable exc) {
            logService.log(LOG_ERROR, "before post-treating taxes on invoice [" + invoiceId
                    + "]: invoice user API is not available", exc);
            throw exc;
        } catch (InvoiceApiException exc) {
            logService.log(LOG_ERROR, "before post-treating taxes on invoice [" + invoiceId
                    + "]: invoice cannot be fetched", exc);
            throw new RuntimeException("unexpected error before post-treating taxes on invoice [" + invoiceId + "]",
                    exc);
        }

        CallContext callCtx = new PluginCallContext(PLUGIN_NAME, DateTime.now(), tenantId);

        TaxComputationContext taxCtx = createTaxComputationContext(newInvoice, callCtx);
        TaxResolver taxResolver = instanciateTaxResolver(taxCtx);
        Map<UUID, TaxCode> newTaxCodes = addMissingTaxCodes(newInvoice, taxResolver, taxCtx, callCtx);

        // Since we're coming from the bus, we need to log-in manually
        // TODO The plugin should have its own table instead of relying on custom fields for this
        killbillAPI.getSecurityApi().login("admin", "password");

        for (Entry<UUID, TaxCode> entry : newTaxCodes.entrySet()) {
            UUID invoiceItemId = entry.getKey();
            TaxCode taxCode = entry.getValue();
            // Need to do it by listening to the event since we cannot add custom fields until the invoice is created
            persistTaxCode(taxCode, invoiceItemId, newInvoice, callCtx);
        }
    }

    /**
     * Pre-compute data that will be useful to computing tax items and tax
     * adjustment items.
     *
     * @param newInvoice
     *            The invoice that is being created.
     * @param tenantCtx
     *            The context in which this code is running.
     * @return An immutable holder for helpful pre-computed data when adding or
     *         adjusting taxes in the account invoices. Never {@code null}.
     */
    private TaxComputationContext createTaxComputationContext(Invoice newInvoice, TenantContext tenantCtx) {

        SimpleTaxConfig cfg = configHandler.getConfigurable(tenantCtx.getTenantId());

        UUID accountId = newInvoice.getAccountId();
        Account account = getAccount(accountId, tenantCtx);
        CustomField taxCountryField = customFieldService.findFieldByNameAndAccountAndTenant(
                TAX_COUNTRY_CUSTOM_FIELD_NAME, accountId, tenantCtx);
        Country accountTaxCountry = null;
        if (taxCountryField != null) {
            try {
                accountTaxCountry = new Country(taxCountryField.getFieldValue());
            } catch (IllegalArgumentException exc) {
                logService.log(LOG_ERROR, "Illegal value of [" + taxCountryField.getFieldValue() + "] in field '"
                        + TAX_COUNTRY_CUSTOM_FIELD_NAME + "' for account " + accountId, exc);
            }
        }

        Set<Invoice> allInvoices = allInvoicesOfAccount(account, newInvoice, tenantCtx);

        Function<InvoiceItem, BigDecimal> toAdjustedAmount = toAdjustedAmount(allInvoices);
        Ordering<InvoiceItem> byAdjustedAmount = natural().onResultOf(toAdjustedAmount);

        TaxCodeService taxCodeService = taxCodeService(account, allInvoices, cfg, tenantCtx);

        return new TaxComputationContext(cfg, account, accountTaxCountry, allInvoices, toAdjustedAmount,
                byAdjustedAmount, taxCodeService);
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
     *            The new invoice that is being created, which might have
     *            already been saved or not.
     * @param tenantCtx
     *            The context in which this code is running.
     * @return A new immutable set of all invoices for the account, including
     *         the new one being created. Never {@code null}, and guaranteed not
     *         having any {@code null} elements.
     */
    private Set<Invoice> allInvoicesOfAccount(Account account, Invoice newInvoice, TenantContext tenantCtx) {
        ImmutableSet.Builder<Invoice> builder = ImmutableSet.builder();
        builder.addAll(getInvoicesByAccountId(account.getId(), tenantCtx));

        // Workaround for https://github.com/killbill/killbill/issues/265
        builder.add(newInvoice);

        return builder.build();
    }

    /**
     * Creates the {@linkplain Function function} that returns the adjusted
     * amount out of a given {@linkplain InvoiceItem invoice item}.
     *
     * @param allInvoices
     *            The collection of all invoices for a given account.
     * @return The function that returns the adjusted amount of an invoice item.
     *         Never {@code null}.
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

    /**
     * Groups the {@linkplain #isAdjustmentItem adjustment items} (found in a
     * given set of invoices) by the identifier of their
     * {@linkplain InvoiceItem#getLinkedItemId related} “adjusted” items.
     * <p>
     * The resulting collection is typically computed on all invoices of a given
     * account.
     *
     * @param allInvoices
     *            A list of invoices.
     * @return A new immutable multi-map of the adjustment items, grouped by the
     *         items they relate to. Never {@code null}, and guaranteed not
     *         having any {@code null} elements.
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
     * Creates an instance of a tax code service.
     *
     * @param account
     *            The account to consider.
     * @param allInvoices
     *            The collection of all invoices for the given account.
     * @param cfg
     *            The plugin configuration.
     * @param tenantCtx
     *            The context in which this code is running.
     * @return A new tax codes service.
     */
    private TaxCodeService taxCodeService(Account account, Set<Invoice> allInvoices, SimpleTaxConfig cfg,
            final TenantContext tenantCtx) {
        CheckedSupplier<StaticCatalog, CatalogApiException> catalog = new CheckedLazyValue<StaticCatalog, CatalogApiException>() {
            @Override
            protected StaticCatalog initialize() throws CatalogApiException {
                return services().getCatalogUserApi().getCurrentCatalog(null, tenantCtx);
            }
        };
        SetMultimap<UUID, CustomField> taxFieldsOfAllInvoices = taxFieldsOfInvoices(account, allInvoices, tenantCtx);
        return new TaxCodeService(catalog, cfg, taxFieldsOfAllInvoices);
    }

    /**
     * Groups the {@linkplain CustomField custom fields} on
     * {@linkplain #INVOICE_ITEM invoice items} by the
     * {@linkplain Invoice#getId() identifier} of their related
     * {@linkplain Invoice invoices}.
     *
     * @param account
     *            The account to consider
     * @param allInvoices
     *            The collection of all invoices for the given account.
     * @param tenantCtx
     *            The context in which this code is running.
     * @return A new immutable multi-map containing the custom fields on all
     *         invoice items of the given account, grouped by the identifier of
     *         their relate invoice. Never {@code null}, and guaranteed not
     *         having any {@code null} elements.
     */
    private SetMultimap<UUID, CustomField> taxFieldsOfInvoices(Account account, Set<Invoice> allInvoices,
            TenantContext tenantCtx) {
        CustomFieldUserApi customFieldsService = services().getCustomFieldUserApi();
        List<CustomField> allCustomFields = customFieldsService.getCustomFieldsForAccountType(account.getId(),
                INVOICE_ITEM, tenantCtx);
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
     * Instantiates the configured {@link TaxResolver} implementation. When
     * instantiation fails, a fail-safe {@link NullTaxResolver} is returned.
     *
     * @param taxCtx
     *            The context data to use when resolving tax codes.
     * @return A new instance of the configured {@link TaxResolver}, or an
     *         instance of {@link NullTaxResolver} if none was configured. Never
     *         {@code null}.
     */
    private TaxResolver instanciateTaxResolver(TaxComputationContext taxCtx) {
        Constructor<? extends TaxResolver> constructor = taxCtx.getConfig().getTaxResolverConstructor();
        Throwable issue;
        try {
            return constructor.newInstance(taxCtx);
        } catch (IllegalAccessException shouldNeverHappen) {
            // This should not happen because we are supposed to deal with a
            // public constructor by SimpleTaxConfig contract. Let it crash.
            throw new RuntimeException(shouldNeverHappen);
        } catch (IllegalArgumentException shouldNeverHappen) {
            // This should not happen because by SimpleTaxConfig contract, we
            // are supposed to deal with a constructor that accepts the expected
            // arguments types. Let it crash.
            throw shouldNeverHappen;
        } catch (InstantiationException exc) {
            issue = exc;
        } catch (InvocationTargetException exc) {
            issue = exc;
        } catch (ExceptionInInitializerError err) {
            issue = err;
        }
        logService.log(LOG_ERROR, "Cannot instanciate tax resolver. Defaulting to [" + NullTaxResolver.class.getName()
                + "].", issue);
        return new NullTaxResolver(taxCtx);
    }

    /**
     * Creates an lists the tax codes that are missing to the new invoice being
     * created.
     *
     * @param newInvoice
     *            The new invoice that is being created.
     * @param resolver
     *            The tax resolver to use.
     * @param taxCtx
     *            The context data to use when computing taxes.
     * @param callCtx
     *            The context in which this code is running.
     * @return A new immutable map of the tax codes to add, mapped from their
     *         related invoice item identifier. Never {@code null}, and
     *         guaranteed not having any {@code null} elements.
     */
    private Map<UUID, TaxCode> addMissingTaxCodes(Invoice newInvoice, TaxResolver resolver,
            final TaxComputationContext taxCtx, CallContext callCtx) {
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
            // Note: expectedTaxCodes != null as per the Multimap contract
            if (expectedTaxCodes.isEmpty()) {
                continue;
            }
            Set<TaxCode> existingTaxCodes = existingTaxCodesForInvoiceItems.get(item.getId());
            // Note: existingTaxCodes != null as per the Multimap contract
            if (!existingTaxCodes.isEmpty()) {
                // Don't override existing tax codes
                continue;
            }

            final String accountTaxCountry = taxCtx.getAccountTaxCountry() == null ? null : taxCtx
                    .getAccountTaxCountry().getCode();
            Iterable<TaxCode> expectedInAccountCountry = filter(expectedTaxCodes, new Predicate<TaxCode>() {
                @Override
                public boolean apply(TaxCode taxCode) {
                    Country restrict = taxCode.getCountry();
                    return (restrict == null) || restrict.getCode().equals(accountTaxCountry);
                }
            });
            // resolve tax codes using regulation-specific logic
            TaxCode applicableCode = resolver.applicableCodeForItem(expectedInAccountCountry, item);
            if (applicableCode == null) {
                continue;
            }

            newTaxCodes.put(item.getId(), applicableCode);
        }
        return newTaxCodes.build();
    }

    private void persistTaxCode(TaxCode applicableCode, UUID invoiceItemId, Invoice newInvoice, CallContext callCtx) {
        CustomFieldUserApi customFieldsService = services().getCustomFieldUserApi();
        ImmutableCustomField.Builder taxCodesField = ImmutableCustomField.builder()//
                .withFieldName(TAX_CODES_FIELD_NAME)//
                .withFieldValue(applicableCode.getName())//
                .withObjectType(INVOICE_ITEM)//
                .withObjectId(invoiceItemId);
        CustomField field = taxCodesField.build();
        try {
            customFieldsService.addCustomFields(newArrayList(field), callCtx);
        } catch (CustomFieldApiException exc) {
            logService.log(LOG_ERROR,
                    "Cannot add custom field [" + field.getFieldName() + "] with value [" + field.getFieldValue()
                    + "] to invoice item [" + invoiceItemId + "] of invoice [" + newInvoice.getId()
                    + "] for tenant [" + callCtx.getTenantId() + "]", exc);
            throw new RuntimeException("unexpected error while adding custom field [" + field.getFieldName()
                    + "] with value [" + field.getFieldValue() + "] to invoice item [" + invoiceItemId
                    + "] of invoice [" + newInvoice.getId() + "] for tenant [" + callCtx.getTenantId() + "]", exc);
        } catch (IllegalStateException exc) {
            if (!"org.killbill.billing.util.callcontext.InternalCallContextFactory$ObjectDoesNotExist".equals(exc
                    .getClass().getName())) {
                throw exc;
            }
            logService.log(
                    LOG_ERROR,
                    "Cannot add custom field [" + field.getFieldName() + "] with value [" + field.getFieldValue()
                    + "] to *non-existing* invoice item [" + invoiceItemId + "] of invoice ["
                    + newInvoice.getId() + "] for tenant [" + callCtx.getTenantId() + "]", exc);
        }
    }

    /**
     * Compute tax items against taxable items, in a <em>newly created</em>
     * invoice, or adjust existing tax items that don't match the expected tax
     * amount, taking any adjustments into consideration.
     *
     * @param newInvoice
     *            The new invoice being created.
     * @param ctx
     *            The context data to use.
     * @param newTaxCodes
     *            The map of new tax code that have just been created for the
     *            given invoice.
     * @return A new immutable list of new tax items, or new adjustment items to
     *         add to the invoice. Never {@code null}, and guaranteed not having
     *         any {@code null} elements.
     */
    private List<InvoiceItem> computeTaxOrAdjustmentItemsForNewInvoice(Invoice newInvoice, TaxComputationContext ctx,
            Map<UUID, TaxCode> newTaxCodes) {

        SetMultimap<UUID, InvoiceItem> currentTaxItems = taxItemsGroupedByRelatedTaxedItems(newInvoice);

        SetMultimap<UUID, TaxCode> existingTaxCodes = ctx.getTaxCodeService().findExistingTaxCodes(newInvoice);

        ImmutableList.Builder<InvoiceItem> newItems = ImmutableList.builder();
        for (InvoiceItem item : newInvoice.getInvoiceItems()) {
            if (!isTaxableItem(item)) {
                continue;
            }

            TaxCode tax = null;
            Set<TaxCode> taxes = existingTaxCodes.get(item.getId());
            // Note: taxes != null as per the Multimap contract
            if (!taxes.isEmpty()) {
                tax = taxes.iterator().next();
            }
            if (tax == null) {
                tax = newTaxCodes.get(item.getId());
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
        return newItems.build();
    }

    /**
     * Groups the {@linkplain #isTaxItem tax items} (found in a given invoice)
     * by the identifier of their {@linkplain InvoiceItem#getLinkedItemId
     * related} “taxable” items.
     *
     * @param invoice
     *            An invoice.
     * @return An immutable multi-map of the tax items for the given invoice,
     *         grouped by the identifier of their related (taxed) item. Never
     *         {@code null}, and guaranteed not having any {@code null}
     *         elements.
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

    /**
     * Computes the amount of tax for a given amount, in the context of a given
     * invoice item, invoice, and account.
     *
     * @param item
     *            The invoice item to tax.
     * @param amount
     *            The adjusted amount of the item to tax.
     * @param tax
     *            The definition of the tax code to apply.
     * @param cfg
     *            The plugin configuration.
     * @return The amount of tax that should be paid by the account.
     */
    private BigDecimal computeTaxAmount(InvoiceItem item, BigDecimal amount, @Nullable TaxCode tax, SimpleTaxConfig cfg) {
        if (tax == null) {
            return ZERO;
        }
        return amount.multiply(tax.getRate()).setScale(cfg.getTaxAmountPrecision(), HALF_UP);
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
     * @return A new tax item, or {@code null}.
     * @throws IllegalArgumentException
     *             if {@code taxableItem} is not {@linkplain #isTaxableItem of a
     *             taxable type}.
     * @see org.killbill.billing.plugin.api.invoice.PluginTaxCalculator#buildTaxItem
     */
    private InvoiceItem buildTaxItem(InvoiceItem taxableItem, LocalDate date, BigDecimal taxAmount,
            @Nonnull String description) {
        checkArgument(isTaxableItem(taxableItem), "not of a taxable type: %s", taxableItem.getInvoiceItemType());
        if ((taxAmount == null) || (ZERO.compareTo(taxAmount) == 0)) {
            // This can actually not happen in our current code. We just keep it
            // because it was taken from the API helper methods.
            return null;
        }
        return createTaxItem(taxableItem, taxableItem.getInvoiceId(), date, null, taxAmount, description);
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
     *            The tax item to be adjusted, which <em>must</em> be
     *            {@linkplain #isTaxItem of a tax type}.
     * @param date
     *            The date at which the tax item above is adjusted. This
     *            typically is the date of the new invoice, the creation of
     *            which has triggered the adjustment.
     * @param adjustmentAmount
     *            The (optional) adjustment amount
     * @param description
     *            An optional description for the adjustment item to create, or
     *            {@code null} if a default description is fine.
     * @return An new adjustment item, or {@code null}.
     * @throws IllegalArgumentException
     *             if {@code taxItemToAdjust} is not {@linkplain #isTaxItem of a
     *             tax type}.
     */
    private InvoiceItem buildAdjustmentForTaxItem(InvoiceItem taxItemToAdjust, LocalDate date,
            @Nullable BigDecimal adjustmentAmount, @Nonnull String description) {
        checkArgument(isTaxItem(taxItemToAdjust), "not a tax type: %s", taxItemToAdjust.getInvoiceItemType());
        if ((adjustmentAmount == null) || (ZERO.compareTo(adjustmentAmount) == 0)) {
            // This can actually not happen in our current code. We just keep it
            // because it was taken from the API helper methods.
            return null;
        }
        return createAdjustmentItem(taxItemToAdjust, taxItemToAdjust.getInvoiceId(), date, null, adjustmentAmount,
                description);
    }

    /**
     * Compute adjustment items on existing tax items in a <em>historical</em>
     * invoice.
     * <p>
     * Tax codes are allowed to change on historical invoice. They can be
     * removed, changed or added. Then taxes are adjusted or added accordingly.
     *
     * @param oldInvoice
     *            An historical invoice.
     * @param ctx
     *            The context data to use.
     * @return A new immutable list of new adjustment items to add to the
     *         invoice. Never {@code null}, and guaranteed not having any
     *         {@code null} elements.
     */
    private List<InvoiceItem> computeTaxOrAdjustmentItemsForHistoricalInvoice(Invoice oldInvoice,
            TaxComputationContext ctx) {

        SetMultimap<UUID, InvoiceItem> currentTaxItems = taxItemsGroupedByRelatedTaxedItems(oldInvoice);

        SetMultimap<UUID, TaxCode> existingTaxCodes = ctx.getTaxCodeService().findExistingTaxCodes(oldInvoice);

        ImmutableList.Builder<InvoiceItem> newItems = ImmutableList.builder();
        for (InvoiceItem item : oldInvoice.getInvoiceItems()) {
            if (!isTaxableItem(item)) {
                continue;
            }

            Set<InvoiceItem> relatedTaxItems = currentTaxItems.get(item.getId());

            TaxCode tax = null;
            Set<TaxCode> taxes = existingTaxCodes.get(item.getId());
            // Note: taxes != null as per the Multimap contract
            if (taxes.isEmpty()) {
                // For safety, to avoid adjusting old invoices without tax codes
                return newItems.build();
            } else {
                tax = taxes.iterator().next();
            }

            BigDecimal adjustedAmount = ctx.toAdjustedAmount().apply(item);
            BigDecimal expectedTaxAmount = computeTaxAmount(item, adjustedAmount, tax, ctx.getConfig());
            BigDecimal currentTaxAmount = sumAmounts(transform(relatedTaxItems, ctx.toAdjustedAmount()));

            if (currentTaxAmount.compareTo(expectedTaxAmount) != 0) {
                BigDecimal adjustmentAmount = expectedTaxAmount.subtract(currentTaxAmount);

                if (relatedTaxItems.isEmpty()) {
                    // Here tax != null because with relatedTaxItem == null we
                    // necessarily have a zero currentTaxAmount, so
                    // expectedTaxAmount is not zero and necessarily results
                    // from a tax code
                    InvoiceItem taxItem = buildTaxItem(item, oldInvoice.getInvoiceDate(), adjustmentAmount,
                            tax.getTaxItemDescription());
                    newItems.add(taxItem);
                } else {
                    // here we have a tax item but the tax code might have been
                    // removed, so it could be null
                    InvoiceItem largestTaxItem = ctx.byAdjustedAmount().max(relatedTaxItems);
                    String taxItemDescription = tax != null ? tax.getTaxItemDescription() : largestTaxItem
                            .getDescription();
                    InvoiceItem adjItem = buildAdjustmentForTaxItem(largestTaxItem, oldInvoice.getInvoiceDate(),
                            adjustmentAmount, taxItemDescription);
                    newItems.add(adjItem);
                }
            }
        }
        return newItems.build();
    }
}
