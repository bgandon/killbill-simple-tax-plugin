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
import com.google.common.collect.ImmutableList;
import org.killbill.billing.plugin.api.PluginTenantContext;
import org.killbill.billing.plugin.simpletax.internal.VATIN;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import static org.killbill.billing.plugin.simpletax.config.http.CustomFieldService.VATIN_CUSTOM_FIELD_NAME;

/**
 * A controller that serves the end points related to VAT Identification Numbers
 * (VATINs).
 *
 * @author Benjamin Gandon
 */
public class VatinController {
    private static final Logger logger = LoggerFactory.getLogger(VatinController.class);
    private CustomFieldService customFieldService;

    /**
     * Constructs a new controller for the end points related to VAT
     * Identification Numbers (VATINs).
     *
     * @param customFieldService
     *            The service to use when accessing custom fields.
     */
    public VatinController(CustomFieldService customFieldService) {
        super();
        this.customFieldService = customFieldService;
    }

    /**
     * Lists JSON resources for VAT Identification Numbers (VATINs) taking any
     * restrictions into account.
     *
     * @param accountId
     *            Any account on which the VAT Identification Numbers (VATINs)
     *            should be restricted. Might be {@code null}.
     * @param tenant
     *            The tenant on which to operate.
     * @return A list of {@linkplain VATINRsc account VAT Identification Number
     *         (VATIN) resources}. Never {@code null}.
     */
    // TODO: return a List<VATINRsc>
    public Object listVatins(@Nullable UUID accountId, @Nonnull Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(accountId, tenant.getId());

        List<CustomField> fields;
        if (accountId == null) {
            fields = customFieldService
                    .findAllAccountFieldsByFieldNameAndTenant(VATIN_CUSTOM_FIELD_NAME, tenantContext);
        } else {
            CustomField field = customFieldService.findFieldByNameAndAccountAndTenant(
                    VATIN_CUSTOM_FIELD_NAME, accountId, tenantContext);
            if (field == null) {
                return ImmutableList.of();
            }
            fields = ImmutableList.of(field);
        }

        List<VATINRsc> vatins = newArrayList();
        for (CustomField field : fields) {
            VATINRsc vatin = toVATINJsonOrNull(field.getObjectId(), field.getFieldValue());
            if (vatin != null) {
                vatins.add(vatin);
            }
        }
        return vatins;
    }

    /**
     * Returns a JSON resource for any VAT Identification Number that could be
     * attached to the given account.
     *
     * @param accountId
     *            An account the VAT Identification Number of which should be
     *            returned. Must not be {@code null}.
     * @param tenant
     *            The tenant on which to operate.
     * @return The {@linkplain VATINRsc VAT Identification Number resource} of
     *         the given account, or {@code null} if none exists.
     */
    // TODO: return a VATINRsc
    public Object getAccountVatin(@Nonnull UUID accountId, @Nonnull Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(accountId, tenant.getId());

        CustomField field = customFieldService.findFieldByNameAndAccountAndTenant(VATIN_CUSTOM_FIELD_NAME,
                accountId, tenantContext);
        if (field == null) {
            return null;
        }
        return toVATINJsonOrNull(accountId, field.getFieldValue());
    }

    /**
     * Persists a new VAT Identification Number value for a given account.
     *
     * @param accountId
     *            An account the VAT Identification Number of which should be
     *            modified. Must not be {@code null}.
     * @param vatinRsc
     *            A new VAT Identification Number resource to persist. Must not
     *            be {@code null}.
     * @param tenant
     *            The tenant on which to operate.
     * @return {@code true} if the VAT Identification Number is properly saved,
     *         or {@code false} otherwise.
     * @throws NullPointerException
     *             When {@code vatinRsc} is {@code null}.
     */
    public boolean saveAccountVatin(@Nonnull UUID accountId, @Nonnull VATINRsc vatinRsc, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(accountId, tenant.getId());
        String newValue = vatinRsc.vatin.getNumber();
        return customFieldService.saveAccountField(newValue, VATIN_CUSTOM_FIELD_NAME, accountId, tenantContext);
    }

    private VATINRsc toVATINJsonOrNull(UUID accountId, String vatin) {
        VATIN vatinObj;
        try {
            vatinObj = new VATIN(vatin);
        } catch (IllegalArgumentException exc) {
            logger.info("Illegal value of [" + vatin + "] in field '" + VATIN_CUSTOM_FIELD_NAME
                    + "' for account " + accountId, exc);
            return null;
        }
        return new VATINRsc(accountId, vatinObj);
    }

    /**
     * A resource for an account VAT Identification Number.
     *
     * @author Benjamin Gandon
     */
    public static final class VATINRsc {
        /**
         * The identifier of the account this VAT Identification Number belongs
         * to.
         */
        // TODO: have immutable resources. Convert to final field?
        public UUID accountId;
        /** The VAT Identification Number. */
        // TODO: have immutable resources. Convert to final field?
        public VATIN vatin;

        /**
         * Constructs a new VAT Identification Number resource.
         *
         * @param accountId
         *            An account identifier.
         * @param vatin
         *            A VAT Identification Number.
         */
        @JsonCreator
        public VATINRsc(@JsonProperty("accountId") UUID accountId, @JsonProperty("vatin") VATIN vatin) {
            // TODO: have more reliable resources. Add
            // Precondition.checkNonNull()
            this.accountId = accountId;
            this.vatin = vatin;
        }
    }
}
