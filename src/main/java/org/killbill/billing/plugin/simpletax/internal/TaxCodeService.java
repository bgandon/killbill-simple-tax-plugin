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
package org.killbill.billing.plugin.simpletax.internal;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import org.apache.commons.collections4.Transformer;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.util.CheckedSupplier;
import org.killbill.billing.util.customfield.CustomField;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.apache.commons.collections4.map.LazyMap.lazyMap;

/**
 * @author Benjamin Gandon
 */
public class TaxCodeService {

    /**
     * The name of a custom field on invoice items, that can specify any
     * relevant tax code to apply.
     */
    public static final String TAX_CODES_FIELD_NAME = "taxCodes";

    private CheckedSupplier<StaticCatalog, CatalogApiException> catalog;
    private SimpleTaxConfig cfg;
    private SetMultimap<UUID, CustomField> taxFieldsOfInvoices;

    /**
     * Creates a service that helps listing tax codes.
     *
     * @param catalog
     *            The Kill Bill catalog to use.
     * @param cfg
     *            The plugin configuration.
     * @param taxFieldsOfInvoices
     *            The tax fields of all account invoices, grouped by their
     *            related taxable items.
     */
    public TaxCodeService(CheckedSupplier<StaticCatalog, CatalogApiException> catalog, SimpleTaxConfig cfg,
            SetMultimap<UUID, CustomField> taxFieldsOfInvoices) {
        super();
        this.catalog = catalog;
        this.cfg = cfg;
        this.taxFieldsOfInvoices = taxFieldsOfInvoices;
    }

    /**
     * Enumerate configured tax codes for the items of a given invoice. The
     * order of configured tax codes is preserved.
     * <p>
     * Final resolution is not done here because it represents custom logic that
     * is regulation-dependent.
     *
     * @param invoice
     *            the invoice the items of which need to be taxed.
     * @return An immutable multi-map of unique applicable tax codes, grouped by
     *         the identifiers of their related invoice items. Never
     *         {@code null}, and guaranteed not having any {@code null} values.
     * @throws NullPointerException
     *             when {@code invoice} is {@code null}.
     */
    @Nonnull
    public SetMultimap<UUID, TaxCode> resolveTaxCodesFromConfig(Invoice invoice) {
        ImmutableSetMultimap.Builder<UUID, TaxCode> taxCodesOfInvoiceItems = ImmutableSetMultimap.builder();

        // This lazy map helps us in building a cache for the values we've
        // already met, and allows us an easy-to-understand syntax below.
        Map<String, Product> productOfPlanName = lazyMap(new HashMap<String, Product>(),
                new Transformer<String, Product>() {
                    @Override
                    public Product transform(String planName) {
                        try {
                            Plan plan = catalog.get().findPlan(planName); //findCurrentPlan(planName);
                            return plan.getProduct();
                        } catch (CatalogApiException notFound) {
                            return null;
                        }
                    }
                });

        for (InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            String planName = invoiceItem.getPlanName();
            if (planName == null) {
                continue;
            }

            Product product = productOfPlanName.get(planName);
            if (product == null) {
                continue;
            }

            Set<TaxCode> taxCodes = cfg.getConfiguredTaxCodes(product.getName());
            if (taxCodes.isEmpty()) {
                continue;
            }
            taxCodesOfInvoiceItems.putAll(invoiceItem.getId(), taxCodes);
        }
        return taxCodesOfInvoiceItems.build();
    }

    /**
     * Find tax codes that apply to the items of a given invoice, looking for
     * custom fields named {@value #TAX_CODES_FIELD_NAME} that can be attached
     * to these items.
     *
     * @param invoice
     *            An invoice in which existing tax codes are to be found.
     * @return The existing tax codes, grouped by the identifiers of their
     *         related invoice items. Never {@code null}, and guaranteed not
     *         having any {@code null} values.
     * @throws NullPointerException
     *             when {@code invoice} is {@code null}.
     */
    @Nonnull
    public SetMultimap<UUID, TaxCode> findExistingTaxCodes(Invoice invoice) {
        Set<CustomField> taxFields = taxFieldsOfInvoices.get(invoice.getId());
        // Note: taxFields is not null, by Multimap contract

        ImmutableSetMultimap.Builder<UUID, TaxCode> taxCodesOfInvoiceItems = ImmutableSetMultimap.builder();
        for (CustomField taxField : taxFields) {
            if (!TAX_CODES_FIELD_NAME.equals(taxField.getFieldName())) {
                continue;
            }
            String taxCodesCSV = taxField.getFieldValue();
            if (taxCodesCSV == null) {
                continue;
            }
            UUID invoiceItemId = taxField.getObjectId();
            Set<TaxCode> taxCodes = cfg.findTaxCodes(taxCodesCSV, "from custom field '" + TAX_CODES_FIELD_NAME
                    + "' of invoice item [" + invoiceItemId + "]");
            taxCodesOfInvoiceItems.putAll(invoiceItemId, taxCodes);
        }
        return taxCodesOfInvoiceItems.build();
    }
}
