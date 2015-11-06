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
import static org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxActivator.PLUGIN_NAME;
import static org.osgi.service.log.LogService.LOG_ERROR;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.plugin.simpletax.util.ImmutableCustomField;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CustomFieldService {

    private static final long START_OFFSET = 0L;
    private static final long PAGE_SIZE = 100L;

    private CustomFieldUserApi customFieldsApi;
    private OSGIKillbillLogService logService;

    public CustomFieldService(CustomFieldUserApi customFieldsApi, OSGIKillbillLogService logService) {
        super();
        this.customFieldsApi = customFieldsApi;
        this.logService = logService;
    }

    public List<CustomField> searchFieldsOfTenant(String searchTerm, TenantContext tenantContext) {
        List<CustomField> fields = newArrayList();
        Pagination<CustomField> page = customFieldsApi.searchCustomFields(searchTerm, START_OFFSET, PAGE_SIZE,
                tenantContext);
        while (Iterables.addAll(fields, page)) {
            page = customFieldsApi.searchCustomFields(searchTerm, page.getNextOffset(), PAGE_SIZE, tenantContext);
        }
        return fields;
    }

    public List<CustomField> listFieldsOfAccount(UUID accountId, TenantContext tenantContext) {
        List<CustomField> fields = customFieldsApi.getCustomFieldsForObject(accountId, ACCOUNT, tenantContext);
        if (fields == null) {
            fields = ImmutableList.of();
        }
        return fields;
    }

    public boolean saveAccountField(String fieldValue, String fieldName, UUID accountId, TenantContext tenantContext) {
        CustomField existing = null;
        List<CustomField> accountFields = customFieldsApi.getCustomFieldsForObject(accountId, ACCOUNT, tenantContext);
        for (CustomField field : accountFields) {
            if (field.getFieldName().equals(fieldName)) {
                existing = field;
                break;
            }
        }
        CallContext context = new PluginCallContext(PLUGIN_NAME, new DateTime(), tenantContext.getTenantId());
        if (existing != null) {
            try {
                customFieldsApi.removeCustomFields(ImmutableList.of(existing), context);
            } catch (CustomFieldApiException exc) {
                logService.log(
                        LOG_ERROR,
                        "while removing custom field '" + existing.getFieldName() + "' with value ["
                                + existing.getFieldValue() + "] on " + existing.getObjectType() + " object ["
                                + existing.getObjectId() + "]", exc);
                return false;
            }
        }
        CustomField newField = ImmutableCustomField.builder()//
                .withObjectType(ACCOUNT).withObjectId(accountId)//
                .withFieldName(fieldName).withFieldValue(fieldValue)//
                .build();
        try {
            customFieldsApi.addCustomFields(ImmutableList.of(newField), context);
            return true;
        } catch (CustomFieldApiException exc) {
            logService.log(LOG_ERROR, "while adding custom field '" + fieldName + "' with value [" + fieldValue
                    + "] on account object [" + accountId + "]", exc);
            try {
                // Add back the removed field
                customFieldsApi.addCustomFields(ImmutableList.of(existing), context);
            } catch (CustomFieldApiException exc2) {
                logService.log(LOG_ERROR,
                        "while adding back the previously removed custom field '" + existing.getFieldName()
                        + "' with value [" + existing.getFieldValue() + "] on " + existing.getObjectType()
                        + " object [" + existing.getObjectId() + "]", exc);
            }
            return false;
        }
    }
}
