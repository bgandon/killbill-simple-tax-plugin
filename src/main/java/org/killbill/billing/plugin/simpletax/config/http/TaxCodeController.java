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
package org.killbill.billing.plugin.simpletax.config.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.api.PluginTenantContext;
import org.killbill.billing.plugin.simpletax.internal.TaxCodeService;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.TAX_CODES_JOIN_SEPARATOR;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.splitTaxCodes;
import static org.killbill.billing.plugin.simpletax.internal.TaxCodeService.TAX_CODES_FIELD_NAME;

/**
 * @author Benjamin Gandon
 */
public class TaxCodeController {
    private static final Logger logger = LoggerFactory.getLogger(TaxCodeController.class);
    private CustomFieldService customFieldService;
    private InvoiceService invoiceService;

    /**
     * @param customFieldService
     *            The service to use when accessing custom fields.
     * @param invoiceService
     *            The service to use when accessing invoices.
     */
    public TaxCodeController(CustomFieldService customFieldService, InvoiceService invoiceService) {
        super();
        this.customFieldService = customFieldService;
        this.invoiceService = invoiceService;
    }

    public List<TaxCodesGETRsc> listInvoiceTaxCodes(@Nonnull UUID invoiceId, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(null, tenant.getId());

        List<InvoiceItem> items = invoiceService.findAllInvoiceItemsByInvoice(invoiceId, tenantContext);

        List<TaxCodesGETRsc> taxCodes = newArrayList();
        for (InvoiceItem item : items) {
            TaxCodesGETRsc rsc = fetchTaxCodesOfInvoiceItem(invoiceId, item.getId(), tenantContext);
            if (rsc != null) {
                taxCodes.add(rsc);
            }
        }

        return taxCodes;
    }

    public boolean saveInvoiceTaxCodes(@Nonnull UUID invoiceId, TaxCodesPOSTRsc taxCodes, Tenant tenant) {
        return saveTaxCodesOfInvoiceItem(taxCodes.invoiceItemId, taxCodes, tenant);
    }

    private static String joinTaxCodes(Set<TaxCodeRsc> taxCodes) {
        StringBuilder names = new StringBuilder();
        for (TaxCodeRsc taxCode : taxCodes) {
            if (names.length() > 0) {
                names.append(TAX_CODES_JOIN_SEPARATOR);
            }
            names.append(taxCode.name);
        }
        return names.toString();
    }

    public TaxCodesGETRsc getTaxCodesOfInvoiceItem(@Nonnull UUID invoiceItemId, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(null, tenant.getId());

        Invoice invoice = invoiceService.findInvoiceByInvoiceItem(invoiceItemId, tenantContext);
        if (invoice == null) {
            logger.info(
                    "No invoice found for invoice item [" + invoiceItemId + "] in tenant [" + tenant.getApiKey() + "]");
            return null;
        }

        return fetchTaxCodesOfInvoiceItem(invoice.getId(), invoiceItemId, tenantContext);
    }

    /**
     * @param invoiceId
     *            Not {@code null}.
     * @param invoiceItemId
     *            Not {@code null}.
     * @param tenantContext
     *            Not {@code null}
     * @return A resource. Or {@code null} if no tax codes are set on the
     *         specified invoice item.
     */
    private TaxCodesGETRsc fetchTaxCodesOfInvoiceItem(@Nonnull UUID invoiceId, @Nonnull UUID invoiceItemId,
            TenantContext tenantContext) {
        CustomField field = customFieldService.findFieldByNameAndInvoiceItemAndTenant(
                // TODO: think about any better place for TAX_CODES_FIELD_NAME
                TaxCodeService.TAX_CODES_FIELD_NAME, invoiceItemId, tenantContext);
        if (field == null) {
            return null;
        }

        return toTaxCodesGETRscOrNull(invoiceId, field.getObjectId(), field.getFieldValue());
    }

    public boolean saveTaxCodesOfInvoiceItem(@Nonnull UUID invoiceItemId, TaxCodesPUTRsc taxCodes, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(null, tenant.getId());
        return customFieldService.saveInvoiceItemField(joinTaxCodes(taxCodes.taxCodes), TAX_CODES_FIELD_NAME,
                invoiceItemId, tenantContext);
    }

    private static TaxCodesGETRsc toTaxCodesGETRscOrNull(UUID invoiceId, UUID invoiceItemId, String taxCodes) {
        Set<String> names = splitTaxCodes(taxCodes);
        if (names.size() == 0) {
            return null;
        }
        ImmutableSet.Builder<TaxCodeRsc> codes = ImmutableSet.builder();
        for (String name : names) {
            codes.add(new TaxCodeRsc(name));
        }
        return new TaxCodesGETRsc(invoiceItemId, invoiceId, codes.build());
    }

    /**
     * A resource for saving tax codes on a specific invoice item that is
     * externally specified (i.e. in the path info).
     *
     * @author Benjamin Gandon
     */
    public static class TaxCodesPUTRsc {
        public Set<TaxCodeRsc> taxCodes;

        @JsonCreator
        public TaxCodesPUTRsc(@JsonProperty("taxCodes") Set<TaxCodeRsc> taxCodes) {
            super();
            this.taxCodes = taxCodes;
        }
    }

    /**
     * A resource for saving tax codes on an invoice item of a specific invoice
     * that is externally specified (i.e. in the path info).
     *
     * @author Benjamin Gandon
     */
    public static class TaxCodesPOSTRsc extends TaxCodesPUTRsc {
        public UUID invoiceItemId;

        @JsonCreator
        public TaxCodesPOSTRsc(@JsonProperty("invoiceItemId") UUID invoiceItemId,
                @JsonProperty("taxCodes") Set<TaxCodeRsc> taxCodes) {
            super(taxCodes);
            this.invoiceItemId = invoiceItemId;
        }

    }

    /**
     * A resource for describing tax codes an invoice item.
     * <p>
     * This resource is meant to be serialized and transmitted to the client,
     * but not meant to be deserialized from the client.
     *
     * @author Benjamin Gandon
     */
    public static final class TaxCodesGETRsc extends TaxCodesPOSTRsc {
        public UUID invoiceId;

        /**
         * @param invoiceItemId
         *            An
         *            {@linkplain org.killbill.billing.invoice.api.InvoiceItem
         *            invoice item identifier}.
         * @param invoiceId
         *            An
         *            {@linkplain org.killbill.billing.invoice.api.Invoice#getId()
         *            invoice identifier}.
         * @param taxCodes
         *            A set of tax codes, in which the order of elements is
         *            significant.
         */
        public TaxCodesGETRsc(UUID invoiceItemId, UUID invoiceId, Set<TaxCodeRsc> taxCodes) {
            super(invoiceItemId, taxCodes);
            this.invoiceId = invoiceId;
        }

    }

    /**
     * A resource for a tax code, (uniquely) designated by its name.
     *
     * @author Benjamin Gandon
     */
    public static final class TaxCodeRsc {
        public String name;

        @JsonCreator
        public TaxCodeRsc(@JsonProperty("name") String name) {
            super();
            this.name = name;
        }
    }
}
