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
package org.killbill.billing.plugin.simpletax.internal;

import static java.math.BigDecimal.ZERO;
import static org.apache.commons.lang3.ObjectUtils.identityToString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.killbill.billing.test.helpers.TaxCodeBuilder;
import org.testng.annotations.Test;

/**
 * Tests for {@link TaxCode}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestTaxCode {

    private final LocalDate today = new LocalDate("2015-10-26");
    private final LocalDate yesterday = today.minusDays(1);

    private TaxCode taxTT = new TaxCodeBuilder()//
            .withName("toto")//
            .withTaxItemDescription("titi")//
            .withRate(new BigDecimal("0.06713"))//
            .withStartingOn(yesterday)//
            .withStoppingOn(today)//
            .build();

    @Test(groups = "fast")
    public void shouldEnforceEqualityWithConsistentHashCode() {
        // Given
        TaxCode taxA = new TaxCodeBuilder().build();
        TaxCode taxB = new TaxCodeBuilder().build();
        TaxCode taxCname = new TaxCodeBuilder().withName("taxC").build();
        TaxCode taxCrate = new TaxCodeBuilder().withRate(ZERO).build();
        TaxCode taxCdesc = new TaxCodeBuilder().withTaxItemDescription("taxC").build();
        TaxCode taxCstart = new TaxCodeBuilder().withStartingOn(today).build();
        TaxCode taxCstop = new TaxCodeBuilder().withStoppingOn(today).build();
        Object anyObject = new Object();

        // Expect
        assertFalse(taxA.equals(null));

        assertTrue(taxA.equals(taxA));
        assertEquals(taxA.hashCode(), taxA.hashCode());

        assertFalse(taxA.equals(anyObject));

        assertTrue(taxA.equals(taxB));
        assertEquals(taxA.hashCode(), taxB.hashCode());

        assertFalse(taxA.equals(taxCname));
        assertFalse(taxA.equals(taxCrate));
        assertFalse(taxA.equals(taxCdesc));
        assertFalse(taxA.equals(taxCstart));
        assertFalse(taxA.equals(taxCstop));
    }

    @Test(groups = "fast")
    public void shouldBeEqualIgnoringScale() {
        // Given
        TaxCode taxD1 = new TaxCodeBuilder().withRate(new BigDecimal("0.1")).build();
        TaxCode taxD2 = new TaxCodeBuilder().withRate(new BigDecimal("0.100")).build();
        TaxCode taxE = new TaxCodeBuilder().withRate(new BigDecimal("0.2")).build();

        // Expect
        assertTrue(taxD1.equals(taxD2));
        assertTrue(taxD2.equals(taxD1));
        assertFalse(taxD1.equals(taxE));
        assertFalse(taxD2.equals(taxE));
        assertFalse(taxE.equals(taxD1));
        assertFalse(taxE.equals(taxD2));
    }

    @Test(groups = "fast")
    public void shouldComputeHashCode() {
        // Expect
        assertEquals(taxTT.hashCode(), -496492379);
    }

    @Test(groups = "fast")
    public void shouldPrintFields() {
        // Expect
        assertEquals(taxTT.toString(), identityToString(taxTT)//
                + "[name=toto,"//
                + "taxItemDescription=titi,"//
                + "rate=0.06713,"//
                + "startingOn=2015-10-25,"//
                + "stoppingOn=2015-10-26]");
    }

    @Test(groups = "fast")
    public void shouldGetWhatWasSet() {
        // Expect
        assertEquals(taxTT.getName(), "toto");
        assertEquals(taxTT.getTaxItemDescription(), "titi");
        assertEquals(taxTT.getRate(), new BigDecimal("0.06713"));
        assertEquals(taxTT.getStartingOn(), new LocalDate("2015-10-25"));
        assertEquals(taxTT.getStoppingOn(), new LocalDate("2015-10-26"));
    }
}
