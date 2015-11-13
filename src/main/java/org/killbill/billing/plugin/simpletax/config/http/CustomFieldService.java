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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.addAll;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.tryFind;
import static com.google.common.collect.Lists.newArrayList;
import static org.killbill.billing.ObjectType.ACCOUNT;
import static org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxActivator.PLUGIN_NAME;
import static org.osgi.service.log.LogService.LOG_ERROR;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;

/**
 * A service class that eases manipulating custom fields values.
 *
 * @author Benjamin Gandon
 */
public class CustomFieldService {
    /** Name of custom fields for account VAT Identification Numbers. */
    public static final String VATIN_CUSTOM_FIELD_NAME = "VATIdNum";
    /** Name of custom fields for account tax countries. */
    public static final String TAX_COUNTRY_CUSTOM_FIELD_NAME = "taxCountry";

    private static final long START_OFFSET = 0L;
    private static final long PAGE_SIZE = 100L;

    private CustomFieldUserApi customFieldApi;
    private OSGIKillbillLogService logService;

    /**
     * Constructs a service for manipulating custom fields.
     *
     * @param customFieldApi
     *            The Kill Bill service API class to use.
     * @param logService
     *            The Kill Bill logging service to use.
     */
    public CustomFieldService(CustomFieldUserApi customFieldApi, OSGIKillbillLogService logService) {
        super();
        this.customFieldApi = customFieldApi;
        this.logService = logService;
    }

    /**
     * A predicate that only accepts custom fields on
     * {@linkplain org.killbill.billing.ObjectType#ACCOUNT account objects} with
     * a specific name.
     */
    private static class RetainAccountFieldsWithName implements Predicate<CustomField> {
        private String fieldName;

        public RetainAccountFieldsWithName(String fieldName) {
            super();
            checkNotNull(fieldName, "Field name must not be null");
            this.fieldName = fieldName;
        }

        @Override
        public boolean apply(CustomField field) {
            return ACCOUNT.equals(field.getObjectType()) && fieldName.equals(field.getFieldName());
        }
    }

    /**
     * Finds all custom fields on account objects that match a specific field
     * name in the context of a given tenant.
     *
     * @param fieldName
     *            A specific field name that returned fields will match.
     * @param tenantContext
     *            The tenant on which to operate.
     * @return The list of matching custom fields. Never {@code null}.
     */
    @Nonnull
    public List<CustomField> findAllAccountFieldsByFieldNameAndTenant(String fieldName, TenantContext tenantContext) {
        List<CustomField> fields = newArrayList();
        Predicate<CustomField> onlyAccountFieldsWithExpectedName = new RetainAccountFieldsWithName(fieldName);
        Pagination<CustomField> page;
        Long nextOffset = START_OFFSET;
        do {
            page = customFieldApi.searchCustomFields(fieldName, nextOffset, PAGE_SIZE, tenantContext);
            addAll(fields, filter(page, onlyAccountFieldsWithExpectedName));
            nextOffset = page.getNextOffset();
        } while (nextOffset != null);
        return fields;
    }

    /**
     * Finds a custom field on a given account object that matches a specific
     * field name in the context of a given tenant.
     *
     * @param fieldName
     *            A specific field name that any returned field will match.
     * @param accountId
     *            An identifier for an account.
     * @param tenantContext
     *            The tenant on which to operate.
     * @return The matching custom field if any, or {@code null} if no such
     *         field exists.
     */
    @Nullable
    public CustomField findAccountFieldByFieldNameAndAccountAndTenant(String fieldName, UUID accountId,
            TenantContext tenantContext) {
        List<CustomField> accountFields = customFieldApi.getCustomFieldsForObject(accountId, ACCOUNT, tenantContext);
        if (accountFields == null) {
            return null;
        }
        return tryFind(accountFields, new RetainAccountFieldsWithName(fieldName)).orNull();
    }

    /**
     * Persists a new value for a custom field on a given account object.
     *
     * @param fieldValue
     *            The new field value.
     * @param fieldName
     *            The field name.
     * @param accountId
     *            The identifier for the account object.
     * @param tenantContext
     *            The tenant on which to operate.
     * @return {@code true} when the new value is properly saved, or
     *         {@code false} otherwise.
     */
    public boolean saveAccountField(String fieldValue, String fieldName, UUID accountId, TenantContext tenantContext) {
        CustomField existing = null;
        List<CustomField> accountFields = customFieldApi.getCustomFieldsForObject(accountId, ACCOUNT, tenantContext);
        if (accountFields != null) {
            for (CustomField field : accountFields) {
                if (field.getFieldName().equals(fieldName)) {
                    existing = field;
                    break;
                }
            }
        }
        CallContext context = new PluginCallContext(PLUGIN_NAME, new DateTime(), tenantContext.getTenantId());
        if (existing != null) {
            try {
                customFieldApi.removeCustomFields(ImmutableList.of(existing), context);
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
            customFieldApi.addCustomFields(ImmutableList.of(newField), context);
            return true;
        } catch (CustomFieldApiException exc) {
            logService.log(LOG_ERROR, "while adding custom field '" + fieldName + "' with value [" + fieldValue
                    + "] on account object [" + accountId + "]", exc);
            try {
                // Add back the removed field
                customFieldApi.addCustomFields(ImmutableList.of(existing), context);
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
