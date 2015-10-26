package org.killbill.billing.plugin.simpletax.util;

import static java.lang.Thread.sleep;
import static org.killbill.billing.ObjectType.INVOICE_ITEM;
import static org.killbill.billing.plugin.simpletax.util.ImmutableCustomField.builder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;

import java.util.UUID;

import org.killbill.billing.plugin.simpletax.util.ImmutableCustomField.Builder;
import org.killbill.billing.util.customfield.CustomField;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests for {@link ImmutableCustomField}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestImmutableCustomField {

    private UUID someUUID;
    private Builder builderA;

    @BeforeMethod
    public void setup() {
        someUUID = UUID.randomUUID();
        builderA = builder()//
                .withObjectType(INVOICE_ITEM)//
                .withObjectId(someUUID)//
                .withFieldName("fieldName toto")//
                .withFieldValue("fieldValue titi");
    }

    @Test(groups = "fast")
    public void shouldCreateNewField() {
        // When
        CustomField fieldA = builderA.build();

        // Then
        assertNotNull(fieldA);
        assertNotNull(fieldA.getId());
        assertNotNull(fieldA.getCreatedDate());
        assertNotNull(fieldA.getUpdatedDate());
        assertEquals(fieldA.getObjectType(), INVOICE_ITEM);
        assertEquals(fieldA.getObjectId(), someUUID);
        assertEquals(fieldA.getFieldName(), "fieldName toto");
        assertEquals(fieldA.getFieldValue(), "fieldValue titi");
    }

    @Test(groups = "fast")
    public void shouldCreateNewFieldAndCustomize() {
        // When
        CustomField fieldA1 = builderA.build();
        sleepTwoMillliSeconds();
        builderA.withFieldName("tata").withFieldValue("titi");
        CustomField fieldA2 = builderA.build();

        // Then
        assertNotSame(fieldA2, fieldA1);
        assertEquals(fieldA2.getId(), fieldA1.getId());
        assertEquals(fieldA2.getCreatedDate(), fieldA1.getCreatedDate());
        assertNotEquals(fieldA2.getUpdatedDate(), fieldA1.getUpdatedDate());
        assertEquals(fieldA2.getObjectType(), INVOICE_ITEM);
        assertEquals(fieldA2.getObjectId(), someUUID);
        assertEquals(fieldA2.getFieldName(), "tata");
        assertEquals(fieldA2.getFieldValue(), "titi");
    }

    private static void sleepTwoMillliSeconds() {
        try {
            sleep(2);
        } catch (InterruptedException exc) {
            throw new RuntimeException(exc);
        }
    }

    @Test(groups = "fast")
    public void shouldCopyExistingCustomField() {
        // Given
        CustomField fieldA = builderA.build();
        Builder builderB = builder(fieldA);

        // When
        CustomField fieldB = builderB.build();

        // Then
        assertNotNull(fieldB);
        assertNotSame(fieldB, fieldA);
        assertEquals(fieldB.getId(), fieldA.getId());
        assertEquals(fieldB.getCreatedDate(), fieldA.getCreatedDate());
        assertEquals(fieldB.getUpdatedDate(), fieldA.getUpdatedDate());
        assertEquals(fieldB.getObjectType(), fieldA.getObjectType());
        assertEquals(fieldB.getObjectId(), fieldA.getObjectId());
        assertEquals(fieldB.getFieldName(), fieldA.getFieldName());
        assertEquals(fieldB.getFieldValue(), fieldA.getFieldValue());
    }

    @Test(groups = "fast")
    public void shouldCopyExistingCustomFieldAndCustomize() {
        // Given
        CustomField fieldA = builderA.build();
        Builder builderB = builder(fieldA);
        CustomField fieldB1 = builderB.build();
        sleepTwoMillliSeconds();
        builderB.withFieldName("tata").withFieldValue("tutu");

        // When
        CustomField fieldB2 = builderB.build();

        // Then
        assertNotSame(fieldB2, fieldB1);
        assertEquals(fieldB2.getId(), fieldA.getId());
        assertEquals(fieldB2.getCreatedDate(), fieldA.getCreatedDate());
        assertNotEquals(fieldB2.getUpdatedDate(), fieldA.getUpdatedDate());
        assertEquals(fieldB2.getObjectType(), fieldA.getObjectType());
        assertEquals(fieldB2.getObjectId(), fieldA.getObjectId());
        assertEquals(fieldB2.getFieldName(), "tata");
        assertEquals(fieldB2.getFieldValue(), "tutu");
    }
}
