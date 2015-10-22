package org.killbill.billing.test.helpers;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.plugin.simpletax.util.ImmutableCustomField;
import org.killbill.billing.util.customfield.CustomField;

public class CustomFieldBuilder implements Builder<CustomField> {

    private UUID objectId;
    private ObjectType objectType;
    private String fieldName, fieldValue;

    @Override
    public CustomField build() {
        return ImmutableCustomField.builder()//
                .withObjectId(objectId)//
                .withObjectType(objectType)//
                .withFieldName(fieldName)//
                .withFieldValue(fieldValue)//
                .build();
    }

    public CustomFieldBuilder withObjectId(UUID objectId) {
        this.objectId = objectId;
        return this;
    }

    public CustomFieldBuilder withObjectType(ObjectType objectType) {
        this.objectType = objectType;
        return this;
    }

    public CustomFieldBuilder withFieldName(String fieldName) {
        this.fieldName = fieldName;
        return this;
    }

    public CustomFieldBuilder withFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
        return this;
    }
}
