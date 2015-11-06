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

import org.killbill.billing.plugin.api.PluginTenantContext;
import org.killbill.billing.plugin.simpletax.internal.VATIN;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class VatinController {
    private static final String VATIN_CUSTOM_FIELD_NAME = "VATIdNum";

    private OSGIKillbillLogService logService;
    private CustomFieldService customFieldService;

    public VatinController(CustomFieldService customFieldService, OSGIKillbillLogService logService) {
        super();
        this.logService = logService;
        this.customFieldService = customFieldService;
    }

    public Object listVatins(@Nullable UUID accountId, @Nonnull Tenant tenant) throws IOException {
        TenantContext tenantContext = new PluginTenantContext(tenant.getId());

        List<CustomField> fields;
        if (accountId == null) {
            fields = customFieldService.searchFieldsOfTenant(VATIN_CUSTOM_FIELD_NAME, tenantContext);
        } else {
            fields = customFieldService.listFieldsOfAccount(accountId, tenantContext);
        }

        List<VATINRsc> vatins = newArrayList();
        for (CustomField field : fields) {
            if (!ACCOUNT.equals(field.getObjectType()) || !VATIN_CUSTOM_FIELD_NAME.equals(field.getFieldName())) {
                continue;
            }
            VATINRsc vatin = toVATINJsonOrNull(field.getObjectId(), field.getFieldValue());
            if (vatin != null) {
                vatins.add(vatin);
            }
        }
        return vatins;
    }

    public Object getAccountVatin(@Nonnull UUID accountId, @Nonnull Tenant tenant) throws IOException {
        TenantContext tenantContext = new PluginTenantContext(tenant.getId());

        List<CustomField> fields = customFieldService.listFieldsOfAccount(accountId, tenantContext);
        if (fields == null) {
            fields = ImmutableList.of();
        }
        for (CustomField field : fields) {
            if (VATIN_CUSTOM_FIELD_NAME.equals(field.getFieldName())) {
                return toVATINJsonOrNull(accountId, field.getFieldValue());
            }
        }
        return null;
    }

    public boolean saveAccountVatin(UUID accountId, VATINRsc vatinRsc, Tenant tenant) {
        TenantContext tenantContext = new PluginTenantContext(tenant.getId());
        String newValue = vatinRsc.vatin.getNumber();
        return customFieldService.saveAccountField(newValue, VATIN_CUSTOM_FIELD_NAME, accountId, tenantContext);
    }

    private VATINRsc toVATINJsonOrNull(UUID accountId, String vatin) {
        VATIN vatinObj;
        try {
            vatinObj = new VATIN(vatin);
        } catch (IllegalArgumentException exc) {
            logService.log(LOG_ERROR, "Illegal value of [" + vatin + "] in field '" + VATIN_CUSTOM_FIELD_NAME
                    + "' for account " + accountId, exc);
            return null;
        }
        return new VATINRsc(accountId, vatinObj);
    }

    public static final class VATINRsc {
        public UUID accountId;
        public VATIN vatin;

        @JsonCreator
        public VATINRsc(@JsonProperty("accountId") UUID accountId, @JsonProperty("vatin") VATIN vatin) {
            this.accountId = accountId;
            this.vatin = vatin;
        }
    }
}
