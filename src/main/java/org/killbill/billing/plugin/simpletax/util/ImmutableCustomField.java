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
package org.killbill.billing.plugin.simpletax.util;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.util.customfield.CustomField;

import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.joda.time.DateTime.now;

/**
 * An immutable implementation of the {@link CustomField} interface, along with
 * its builder class.
 *
 * @author Benjamin Gandon
 */
public class ImmutableCustomField implements CustomField {

    private UUID id;
    private DateTime createdDate, updatedDate;
    private UUID objectId;
    private ObjectType objectType;
    private String fieldName, fieldValue;

    private ImmutableCustomField() {
        super();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public UUID getObjectId() {
        return objectId;
    }

    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String getFieldValue() {
        return fieldValue;
    }

    /**
     * @return A new {@link ImmutableCustomField} builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param field
     *            A template custom field, the data of which should be copied.
     * @return A new {@link ImmutableCustomField} builder.
     */
    public static Builder builder(CustomField field) {
        return new Builder(field);
    }

    /**
     * A builder class for {@link ImmutableCustomField}s.
     *
     * @author Benjamin Gandon
     */
    public static class Builder {

        private UUID id;
        private DateTime createdDate, updatedDate;
        private UUID objectId;
        private ObjectType objectType;
        private String fieldName, fieldValue;

        private Builder() {
            super();
            id = randomUUID();
            createdDate = now();
            updatedDate = now();
        }

        private Builder(CustomField src) {
            this();
            id = src.getId();
            createdDate = src.getCreatedDate();
            updatedDate = src.getUpdatedDate();
            objectId = src.getObjectId();
            objectType = src.getObjectType();
            fieldName = src.getFieldName();
            fieldValue = src.getFieldValue();
        }

        /**
         * @return A new {@link ImmutableCustomField}, with the properties set
         *         in this builder.
         */
        public CustomField build() {
            ImmutableCustomField taxField = new ImmutableCustomField();
            taxField.id = id;
            taxField.createdDate = createdDate;
            taxField.updatedDate = updatedDate;
            taxField.objectId = objectId;
            taxField.objectType = objectType;
            taxField.fieldName = fieldName;
            taxField.fieldValue = fieldValue;
            return taxField;
        }

        /**
         * @param objectId
         *            The object identifier to use.
         * @return this builder
         */
        public Builder withObjectId(UUID objectId) {
            this.objectId = objectId;
            updatedDate = now();
            return this;
        }

        /**
         * @param objectType
         *            The object type to use.
         * @return this builder
         */
        public Builder withObjectType(ObjectType objectType) {
            this.objectType = objectType;
            updatedDate = now();
            return this;
        }

        /**
         * @param fieldName
         *            The field name to use.
         * @return this builder
         */
        public Builder withFieldName(String fieldName) {
            this.fieldName = fieldName;
            updatedDate = now();
            return this;
        }

        /**
         * @param fieldValue
         *            The field value to use.
         * @return this builder
         */
        public Builder withFieldValue(String fieldValue) {
            this.fieldValue = fieldValue;
            updatedDate = now();
            return this;
        }
    }
}
