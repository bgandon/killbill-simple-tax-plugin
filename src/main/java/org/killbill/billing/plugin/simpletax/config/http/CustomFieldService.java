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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.plugin.simpletax.util.ImmutableCustomField;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.*;
import static com.google.common.collect.Lists.newArrayList;
import static org.killbill.billing.ObjectType.ACCOUNT;
import static org.killbill.billing.ObjectType.INVOICE_ITEM;
import static org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxActivator.PLUGIN_NAME;

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
    private static final Logger logger = LoggerFactory.getLogger(CustomFieldService.class);

    /**
     * Constructs a service for manipulating custom fields.
     *
     * @param customFieldApi
     *            The Kill Bill service API class to use.
     */
    public CustomFieldService(CustomFieldUserApi customFieldApi) {
        super();
        this.customFieldApi = customFieldApi;
    }

    /**
     * A predicate that only accepts custom fields with a specific name.
     */
    private static class RetainFieldsWithName implements Predicate<CustomField> {
        private String fieldName;

        public RetainFieldsWithName(String fieldName) {
            super();
            checkNotNull(fieldName, "Field name must not be null");
            this.fieldName = fieldName;
        }

        @Override
        public boolean apply(CustomField field) {
            return fieldName.equals(field.getFieldName());
        }
    }

    /**
     * A predicate that only accepts custom fields of a given
     * {@linkplain org.killbill.billing.ObjectType object type} and a specific
     * name.
     */
    private static class RetainFieldsWithNameAndObjectType extends RetainFieldsWithName {
        private ObjectType objectType;

        public RetainFieldsWithNameAndObjectType(String fieldName, ObjectType objectType) {
            super(fieldName);
            checkNotNull(objectType, "Object type must not be null");
            this.objectType = objectType;
        }

        @Override
        public boolean apply(CustomField field) {
            return objectType.equals(field.getObjectType()) && super.apply(field);
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
        Predicate<CustomField> onlyAccountFieldsWithExpectedName = new RetainFieldsWithNameAndObjectType(fieldName,
                ACCOUNT);
        Pagination<CustomField> page;
        Long nextOffset = START_OFFSET;
        do {
            page = customFieldApi.searchCustomFields(fieldName, nextOffset, PAGE_SIZE, tenantContext);
            addAll(fields, filter(page, onlyAccountFieldsWithExpectedName));
            nextOffset = page.getNextOffset();
        } while (nextOffset != null);
        return fields;
    }

    @Nullable
    public CustomField findFieldByNameAndInvoiceItemAndTenant(String fieldName, UUID invoiceItemId,
            TenantContext tenantContext) {
        List<CustomField> invoiceItemFields = customFieldApi.getCustomFieldsForObject(invoiceItemId, INVOICE_ITEM,
                tenantContext);
        if (invoiceItemFields == null) {
            return null;
        }
        return tryFind(invoiceItemFields, new RetainFieldsWithName(fieldName)).orNull();
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
    public CustomField findFieldByNameAndAccountAndTenant(String fieldName, UUID accountId, TenantContext tenantContext) {
        List<CustomField> accountFields = customFieldApi.getCustomFieldsForObject(accountId, ACCOUNT, tenantContext);
        if (accountFields == null) {
            return null;
        }
        return tryFind(accountFields, new RetainFieldsWithName(fieldName)).orNull();
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
        return saveAccountField(fieldValue, fieldName, accountId, ACCOUNT, tenantContext);
    }

    public boolean saveInvoiceItemField(String fieldValue, String fieldName, UUID invoiceItemId,
            TenantContext tenantContext) {
        return saveAccountField(fieldValue, fieldName, invoiceItemId, INVOICE_ITEM, tenantContext);
    }

    private boolean saveAccountField(String fieldValue, String fieldName, UUID objectId, ObjectType objectType,
            TenantContext tenantContext) {
        CustomField existing = null;
        List<CustomField> accountFields = customFieldApi.getCustomFieldsForObject(objectId, objectType, tenantContext);
        if (accountFields != null) {
            for (CustomField field : accountFields) {
                if (field.getFieldName().equals(fieldName)) {
                    existing = field;
                    break;
                }
            }
        }
        CallContext context = new PluginCallContext(PLUGIN_NAME, new DateTime(), null, tenantContext.getTenantId());
        if (existing != null) {
            try {
                customFieldApi.removeCustomFields(ImmutableList.of(existing), context);
            } catch (CustomFieldApiException exc) {
                logger.error(
                        "while removing custom field '" + existing.getFieldName() + "' with value ["
                                + existing.getFieldValue() + "] on " + existing.getObjectType() + " object ["
                                + existing.getObjectId() + "]", exc);
                return false;
            }
        }
        CustomField newField = ImmutableCustomField.builder()//
                .withObjectType(objectType).withObjectId(objectId)//
                .withFieldName(fieldName).withFieldValue(fieldValue)//
                .build();
        try {
            customFieldApi.addCustomFields(ImmutableList.of(newField), context);
            return true;
        } catch (CustomFieldApiException exc) {
            logger.error("while adding custom field '" + fieldName + "' with value [" + fieldValue
                    + "] on " + objectType + " object [" + objectId + "]", exc);
            try {
                // Add back the removed field
                customFieldApi.addCustomFields(ImmutableList.of(existing), context);
            } catch (CustomFieldApiException exc2) {
                logger.error(
                        "while adding back the previously removed custom field '" + existing.getFieldName()
                                + "' with value [" + existing.getFieldValue() + "] on " + existing.getObjectType()
                                + " object [" + existing.getObjectId() + "]", exc);
            }
            return false;
        }
    }
}
