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

import static com.google.common.collect.Lists.newArrayList;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.ZERO;
import static java.math.BigDecimal.valueOf;
import static java.util.Arrays.asList;
import static org.killbill.billing.plugin.simpletax.util.InvoiceHelpers.amountWithAdjustments;
import static org.killbill.billing.plugin.simpletax.util.InvoiceHelpers.sumAmounts;
import static org.killbill.billing.plugin.simpletax.util.InvoiceHelpers.sumItems;
import static org.killbill.billing.test.helpers.Assert.assertEqualsIgnoreScale;

import java.math.BigDecimal;
import java.util.UUID;

import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.test.helpers.InvoiceItemBuilder;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * Tests for {@link InvoiceHelpers}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestInvoiceHelpers {

    private static final BigDecimal NINE = valueOf(9L);
    private static final BigDecimal ELEVEN = valueOf(11L);
    private static final BigDecimal EIGHTTEEN = valueOf(18L);

    private InvoiceItem itemA, itemB, itemC, itemD;

    @BeforeClass
    public void init() {
        itemA = new InvoiceItemBuilder()//
                .withAmount(TEN)//
                .build();
        itemB = new InvoiceItemBuilder()//
                .withAmount(null)//
                .build();
        itemC = new InvoiceItemBuilder()//
                .withAmount(ONE)//
                .build();
        itemD = new InvoiceItemBuilder()//
                .withAmount(ONE.negate())//
                .build();
    }

    /** Helper assert that ignores {@linkplain BigDecimal#scale() scales}. */
    private static void assertEquals(BigDecimal actual, BigDecimal expected) {
        assertEqualsIgnoreScale(actual, expected);
    }

    @Test(groups = "fast", expectedExceptions = IllegalAccessException.class)
    public void shouldBeAbstractClass() throws Exception {
        InvoiceHelpers.class.getDeclaredConstructor().newInstance();
    }

    @Test(groups = "fast")
    public void sumItemsShouldReturnZeroForNullInput() {
        // Expect
        assertEquals(sumItems(null), ZERO);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void sumItemsShouldThrowNPEOnNullItem() {
        // Expect NPE
        sumItems(newArrayList(itemA, null, itemB));
    }

    @Test(groups = "fast")
    public void sumItemsShouldSumPositiveItems() {
        // Given
        Iterable<InvoiceItem> items = newArrayList(itemA, itemB, itemC);

        // Expect
        assertEquals(sumItems(items), ELEVEN);
    }

    @Test(groups = "fast")
    public void sumItemsShouldSumNegativeItems() {
        // Given
        Iterable<InvoiceItem> items = newArrayList(itemA, itemB, itemD);

        // Expect
        assertEquals(sumItems(items), NINE);
    }

    /** Shortcut for {@link ImmutableMultimap#of(Object, Object)} */
    private static <K, V> Multimap<K, V> multimap(K k1, V v1) {
        return ImmutableMultimap.of(k1, v1);
    }

    @Test(groups = "fast")
    public void amountWithAdjustmentsShouldIgnoreInexistentAdjustments() {
        // Given
        Multimap<UUID, InvoiceItem> adjustments = multimap(itemC.getId(), itemD);

        // Expect
        assertEquals(amountWithAdjustments(itemA, adjustments), TEN);
    }

    @Test(groups = "fast")
    public void amountWithAdjustmentsShouldAddExistingPositiveAdjustment() {
        // Given
        Multimap<UUID, InvoiceItem> adjustments = multimap(itemA.getId(), itemC);

        // Expect
        assertEquals(amountWithAdjustments(itemA, adjustments), ELEVEN);
    }

    @Test(groups = "fast")
    public void amountWithAdjustmentsShouldIgnoreNullAmounts1() {
        // Given
        ImmutableMultimap.Builder<UUID, InvoiceItem> adjustments = ImmutableMultimap.builder();
        adjustments.put(itemA.getId(), itemB);
        adjustments.put(itemA.getId(), itemC);

        // Expect
        assertEquals(amountWithAdjustments(itemA, adjustments.build()), ELEVEN);
    }

    @Test(groups = "fast")
    public void amountWithAdjustmentsShouldNullAmountAsZero() {
        // Given
        ImmutableMultimap.Builder<UUID, InvoiceItem> adjustments = ImmutableMultimap.builder();
        adjustments.put(itemB.getId(), itemA);
        adjustments.put(itemB.getId(), itemC);
        adjustments.put(itemB.getId(), itemD);

        // Expect
        assertEquals(amountWithAdjustments(itemB, adjustments.build()), TEN);
    }

    @Test(groups = "fast")
    public void amountWithAdjustmentsShouldAddExistingNegativeAdjustment() {
        // Given
        Multimap<UUID, InvoiceItem> adjustments = multimap(itemA.getId(), itemD);

        // Expect
        assertEquals(amountWithAdjustments(itemA, adjustments), NINE);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void sumAmountsShouldThrowNpeOnNullIterable() {
        // Expect NPE
        sumAmounts(null);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void sumAmountsShouldThrowNpeOnNullAmount() {
        // Expect NPE
        sumAmounts(asList(ONE.negate(), null, TEN));
    }

    @Test(groups = "fast")
    public void sumAmountsShouldSumNonNullAmounts() {
        // Given
        Iterable<BigDecimal> amounts = ImmutableList.of(ONE.negate(), NINE, TEN);

        // Expect
        assertEquals(sumAmounts(amounts), EIGHTTEEN);
    }
}
