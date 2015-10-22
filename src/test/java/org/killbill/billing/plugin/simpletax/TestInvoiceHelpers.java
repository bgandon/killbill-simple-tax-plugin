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
package org.killbill.billing.plugin.simpletax;

import static com.google.common.collect.Lists.newArrayList;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.ZERO;
import static java.math.BigDecimal.valueOf;
import static org.killbill.billing.plugin.simpletax.util.InvoiceHelpers.sumItems;
import static org.testng.Assert.assertEquals;

import java.math.BigDecimal;

import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.util.InvoiceHelpers;
import org.killbill.billing.test.helpers.InvoiceItemBuilder;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for {@link InvoiceHelpers}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestInvoiceHelpers {

    private static final BigDecimal ELEVEN = valueOf(11L);

    private InvoiceItem itemA, itemB, itemC;

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
    public void sumItemsShouldSum() {
        // Given
        Iterable<InvoiceItem> items = newArrayList(itemA, itemB, itemC);

        // Expect
        assertEquals(sumItems(items), ELEVEN);
    }
}
