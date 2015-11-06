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

import static com.google.common.collect.Lists.newArrayList;
import static org.killbill.billing.ObjectType.ACCOUNT;
import static org.osgi.service.log.LogService.LOG_ERROR;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;

import org.killbill.billing.plugin.api.PluginTenantContext;
import org.killbill.billing.plugin.simpletax.internal.Country;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class TaxCountryController {
    private static final String TAX_COUNTRY_CUSTOM_FIELD_NAME = "taxCountry";

    private OSGIKillbillLogService logService;
    private CustomFieldService customFieldService;

    public TaxCountryController(CustomFieldService customFieldService, OSGIKillbillLogService logService) {
        super();
        this.logService = logService;
        this.customFieldService = customFieldService;
    }

    public Object listTaxCountries(@Nullable UUID accountId, Tenant tenant, HttpServletResponse resp)
            throws IOException {
        TenantContext tenantContext = new PluginTenantContext(tenant.getId());

        List<CustomField> fields;
        if (accountId == null) {
            fields = customFieldService.searchFieldsOfTenant(TAX_COUNTRY_CUSTOM_FIELD_NAME, tenantContext);
        } else {
            fields = customFieldService.listFieldsOfAccount(accountId, tenantContext);
        }

        List<TaxCountryRsc> taxCountries = newArrayList();
        for (CustomField field : fields) {
            if (!ACCOUNT.equals(field.getObjectType()) || !TAX_COUNTRY_CUSTOM_FIELD_NAME.equals(field.getFieldName())) {
                continue;
            }
            TaxCountryRsc taxCountry = toTaxCountryJsonOrNull(field.getObjectId(), field.getFieldValue());
            if (taxCountry != null) {
                taxCountries.add(taxCountry);
            }
        }
        return taxCountries;
    }

    public Object getAccountTaxCountry(@Nonnull UUID accountId, Tenant tenant) throws IOException {
        TenantContext tenantContext = new PluginTenantContext(tenant.getId());

        List<CustomField> fields = customFieldService.listFieldsOfAccount(accountId, tenantContext);
        if (fields == null) {
            fields = ImmutableList.of();
        }
        for (CustomField field : fields) {
            if (TAX_COUNTRY_CUSTOM_FIELD_NAME.equals(field.getFieldName())) {
                return toTaxCountryJsonOrNull(accountId, field.getFieldValue());
            }
        }
        return null;
    }

    public boolean saveAccountTaxCountry(UUID accountId, TaxCountryRsc taxCountry, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(tenant.getId());
        String newValue = taxCountry.taxCountry.getCode();
        return customFieldService.saveAccountField(newValue, TAX_COUNTRY_CUSTOM_FIELD_NAME, accountId, tenantContext);
    }

    private TaxCountryRsc toTaxCountryJsonOrNull(@Nonnull UUID accountId, @Nullable String country) {
        Country taxCountry;
        try {
            taxCountry = new Country(country);
        } catch (IllegalArgumentException exc) {
            logService.log(LOG_ERROR, "Illegal value of [" + country + "] in field '" + TAX_COUNTRY_CUSTOM_FIELD_NAME
                    + "' for account " + accountId, exc);
            taxCountry = null;
        }
        return new TaxCountryRsc(accountId, taxCountry);
    }

    public static final class TaxCountryRsc {
        public UUID accountId;
        public Country taxCountry;

        @JsonCreator
        public TaxCountryRsc(@JsonProperty("accountId") UUID accountId, @JsonProperty("taxCountry") Country taxCountry) {
            this.accountId = accountId;
            this.taxCountry = taxCountry;
        }
    }
}
