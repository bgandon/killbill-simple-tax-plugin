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
import static org.killbill.billing.plugin.simpletax.config.http.CustomFieldService.TAX_COUNTRY_CUSTOM_FIELD_NAME;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.killbill.billing.plugin.api.PluginTenantContext;
import org.killbill.billing.plugin.simpletax.internal.Country;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A controller that serves the end points related to Tax Countries.
 *
 * @author Benjamin Gandon
 */
public class TaxCountryController {
    private static final Logger logger = LoggerFactory.getLogger(TaxCountryController.class);

    private CustomFieldService customFieldService;

    /**
     * Constructs a new controller for tax country end points.
     *
     * @param customFieldService
     *            The service to use when accessing custom fields.
     */
    public TaxCountryController(CustomFieldService customFieldService) {
        super();
        this.customFieldService = customFieldService;
    }

    /**
     * Lists JSON resources for tax countries taking any restrictions into
     * account.
     *
     * @param accountId
     *            Any account on which the tax countries should be restricted.
     *            Might be {@code null}.
     * @param tenant
     *            The tenant on which to operate.
     * @return A list of {@linkplain TaxCountryRsc account tax countries
     *         resources}. Never {@code null}.
     */
    // TODO: return a List<TaxCountryRsc>
    public Object listTaxCountries(@Nullable UUID accountId, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(accountId, tenant.getId());

        List<CustomField> fields;
        if (accountId == null) {
            fields = customFieldService.findAllAccountFieldsByFieldNameAndTenant(TAX_COUNTRY_CUSTOM_FIELD_NAME,
                    tenantContext);
        } else {
            CustomField field = customFieldService.findFieldByNameAndAccountAndTenant(
                    TAX_COUNTRY_CUSTOM_FIELD_NAME, accountId, tenantContext);
            if (field == null) {
                return ImmutableList.of();
            }
            fields = ImmutableList.of(field);
        }

        List<TaxCountryRsc> taxCountries = newArrayList();
        for (CustomField field : fields) {
            TaxCountryRsc taxCountry = toTaxCountryJsonOrNull(field.getObjectId(), field.getFieldValue());
            if (taxCountry != null) {
                taxCountries.add(taxCountry);
            }
        }
        return taxCountries;
    }

    /**
     * Returns a JSON resource for any tax country that could be attached to the
     * given account.
     *
     * @param accountId
     *            An account the tax country of which should be returned. Must
     *            not be {@code null}.
     * @param tenant
     *            The tenant on which to operate.
     * @return The {@linkplain TaxCountryRsc tax country resource} of the given
     *         account, or {@code null} if none exists.
     */
    // TODO: return a TaxCountryRsc
    public Object getAccountTaxCountry(@Nonnull UUID accountId, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(accountId, tenant.getId());

        CustomField field = customFieldService.findFieldByNameAndAccountAndTenant(
                TAX_COUNTRY_CUSTOM_FIELD_NAME, accountId, tenantContext);
        if (field == null) {
            return null;
        }
        return toTaxCountryJsonOrNull(accountId, field.getFieldValue());
    }

    /**
     * Persists a new tax country value for a given account.
     *
     * @param accountId
     *            An account the tax country of which should be modified. Must
     *            not be {@code null}.
     * @param taxCountryRsc
     *            A new tax country resource to persist. Must not be
     *            {@code null}.
     * @param tenant
     *            The tenant on which to operate.
     * @return {@code true} if the tax country is properly saved, or
     *         {@code false} otherwise.
     * @throws NullPointerException
     *             When {@code vatinRsc} is {@code null}.
     */
    public boolean saveAccountTaxCountry(@Nonnull UUID accountId, @Nonnull TaxCountryRsc taxCountryRsc, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(accountId, tenant.getId());
        String newValue = taxCountryRsc.taxCountry.getCode();
        return customFieldService.saveAccountField(newValue, TAX_COUNTRY_CUSTOM_FIELD_NAME, accountId, tenantContext);
    }

    // TODO: rename to toTaxCountryRscOrNull
    private TaxCountryRsc toTaxCountryJsonOrNull(@Nonnull UUID accountId, @Nullable String country) {
        Country taxCountry;
        try {
            taxCountry = new Country(country);
        } catch (IllegalArgumentException exc) {
            logger.error("Illegal value of [" + country + "] in field '" + TAX_COUNTRY_CUSTOM_FIELD_NAME
                    + "' for account " + accountId, exc);
            return null;
        }
        return new TaxCountryRsc(accountId, taxCountry);
    }

    /**
     * A resource for an account tax country.
     *
     * @author Benjamin Gandon
     */
    public static final class TaxCountryRsc {
        /** The identifier of the account this tax country belongs to. */
        // TODO: have immutable resources. Convert to final field?
        public UUID accountId;
        /** The tax country. */
        // TODO: have immutable resources. Convert to final field?
        public Country taxCountry;

        /**
         * Constructs a new tax country resource.
         *
         * @param accountId
         *            An account identifier.
         * @param taxCountry
         *            A tax country.
         */
        @JsonCreator
        public TaxCountryRsc(@JsonProperty("accountId") UUID accountId, @JsonProperty("taxCountry") Country taxCountry) {
            // TODO: have more reliable resources. Add
            // Precondition.checkNonNull()
            this.accountId = accountId;
            this.taxCountry = taxCountry;
        }
    }
}
