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
import static org.killbill.billing.plugin.simpletax.config.http.CustomFieldService.TAX_ZONE_CUSTOM_FIELD_NAME;
import static org.osgi.service.log.LogService.LOG_ERROR;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.killbill.billing.plugin.api.PluginTenantContext;
import org.killbill.billing.plugin.simpletax.internal.TaxZone;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

/**
 * A controller that serves the end points related to Tax Zones.
 *
 * @author Benjamin Gandon
 */
public class TaxZoneController {
    private OSGIKillbillLogService logService;
    private CustomFieldService customFieldService;

    /**
     * Constructs a new controller for tax zone end points.
     *
     * @param customFieldService
     *            The service to use when accessing custom fields.
     * @param logService
     *            The Kill Bill log service to use.
     */
    public TaxZoneController(CustomFieldService customFieldService, OSGIKillbillLogService logService) {
        super();
        this.logService = logService;
        this.customFieldService = customFieldService;
    }

    /**
     * Lists JSON resources for tax zones taking any restrictions into account.
     *
     * @param accountId
     *            Any account on which the tax zones should be restricted. Might
     *            be {@code null}.
     * @param tenant
     *            The tenant on which to operate.
     * @return A list of {@linkplain TaxZoneRsc account tax zones resources} .
     *         Never {@code null}.
     */
    // TODO: return a List<TaxZoneRsc>
    public Object listTaxZones(@Nullable UUID accountId, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(tenant.getId());

        List<CustomField> fields;
        if (accountId == null) {
            fields = customFieldService.findAllAccountFieldsByFieldNameAndTenant(TAX_ZONE_CUSTOM_FIELD_NAME,
                    tenantContext);
        } else {
            CustomField field = customFieldService.findFieldByNameAndAccountAndTenant(TAX_ZONE_CUSTOM_FIELD_NAME,
                    accountId, tenantContext);
            if (field == null) {
                return ImmutableList.of();
            }
            fields = ImmutableList.of(field);
        }

        List<TaxZoneRsc> taxZones = newArrayList();
        for (CustomField field : fields) {
            TaxZoneRsc taxZone = toTaxZoneJsonOrNull(field.getObjectId(), field.getFieldValue());
            if (taxZone != null) {
                taxZones.add(taxZone);
            }
        }
        return taxZones;
    }

    /**
     * Returns a JSON resource for any tax zone that could be attached to the
     * given account.
     *
     * @param accountId
     *            An account the tax zone of which should be returned. Must not
     *            be {@code null}.
     * @param tenant
     *            The tenant on which to operate.
     * @return The {@linkplain TaxZoneRsc tax zone resource} of the given
     *         account, or {@code null} if none exists.
     */
    // TODO: return a TaxZoneRsc
    public Object getAccountTaxZone(@Nonnull UUID accountId, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(tenant.getId());

        CustomField field = customFieldService.findFieldByNameAndAccountAndTenant(TAX_ZONE_CUSTOM_FIELD_NAME,
                accountId, tenantContext);
        if (field == null) {
            return null;
        }
        return toTaxZoneJsonOrNull(accountId, field.getFieldValue());
    }

    /**
     * Persists a new tax zone value for a given account.
     *
     * @param accountId
     *            An account the tax zone of which should be modified. Must not
     *            be {@code null}.
     * @param taxZoneRsc
     *            A new tax zone resource to persist. Must not be {@code null}.
     * @param tenant
     *            The tenant on which to operate.
     * @return {@code true} if the tax zone is properly saved, or {@code false}
     *         otherwise.
     * @throws NullPointerException
     *             When {@code vatinRsc} is {@code null}.
     */
    public boolean saveAccountTaxZone(@Nonnull UUID accountId, @Nonnull TaxZoneRsc taxZoneRsc, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(tenant.getId());
        String newValue = taxZoneRsc.taxZone.getCode();
        return customFieldService.saveAccountField(newValue, TAX_ZONE_CUSTOM_FIELD_NAME, accountId, tenantContext);
    }

    // TODO: rename to toTaxZoneRscOrNull
    private TaxZoneRsc toTaxZoneJsonOrNull(@Nonnull UUID accountId, @Nullable String zone) {
        TaxZone taxZone;
        try {
            taxZone = new TaxZone(zone);
        } catch (IllegalArgumentException exc) {
            logService.log(LOG_ERROR, "Illegal value of [" + zone + "] in field '" + TAX_ZONE_CUSTOM_FIELD_NAME
                    + "' for account " + accountId, exc);
            return null;
        }
        return new TaxZoneRsc(accountId, taxZone);
    }

    /**
     * A resource for an account tax zone.
     *
     * @author Benjamin Gandon
     */
    public static final class TaxZoneRsc {
        /** The identifier of the account this tax zone belongs to. */
        // TODO: have immutable resources. Convert to final field?
        public UUID accountId;
        /** The tax zone. */
        // TODO: have immutable resources. Convert to final field?
        public TaxZone taxZone;

        /**
         * Constructs a new tax zone resource.
         *
         * @param accountId
         *            An account identifier.
         * @param taxZone
         *            A tax zone code.
         */
        @JsonCreator
        public TaxZoneRsc(@JsonProperty("accountId") UUID accountId, @JsonProperty("taxZone") TaxZone taxZone) {
            // TODO: have more reliable resources. Add
            // Precondition.checkNonNull()
            this.accountId = accountId;
            this.taxZone = taxZone;
        }
    }
}
