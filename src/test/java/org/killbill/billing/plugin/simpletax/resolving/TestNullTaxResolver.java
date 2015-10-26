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
package org.killbill.billing.plugin.simpletax.resolving;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.test.helpers.InvoiceItemBuilder;
import org.killbill.billing.test.helpers.TaxCodeBuilder;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

/**
 * Tests for {@link NullTaxResolver}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestNullTaxResolver {

    private static final Class<NullTaxResolver> CLAZZ = NullTaxResolver.class;
    private static final Class<TaxComputationContext> TCC_CLASS = TaxComputationContext.class;

    private final LocalDate today = new LocalDate();
    private final LocalDate yesterday = today.minusDays(1);

    @Test(groups = "fast")
    public void shouldHaveConstructorAcceptingTCC() throws Exception {
        assertNotNull(CLAZZ.getConstructor(TCC_CLASS));
    }

    @Test(groups = "fast")
    public void shouldAlwaysReturnNull() {
        // Given
        NullTaxResolver resolver = new NullTaxResolver(null);
        ImmutableSet<TaxCode> noTaxCodes = ImmutableSet.<TaxCode> of();
        InvoiceItem mockItem = mock(InvoiceItem.class);
        ImmutableSet<TaxCode> oneApplicableTaxCode = ImmutableSet.<TaxCode> of(new TaxCodeBuilder().build());
        InvoiceItem itemWithDates = new InvoiceItemBuilder()//
        .withStartDate(yesterday)//
        .withEndDate(today)//
        .build();

        // Expect
        assertNull(resolver.applicableCodeForItem(null, null));
        assertNull(resolver.applicableCodeForItem(noTaxCodes, null));
        assertNull(resolver.applicableCodeForItem(null, mockItem));
        assertNull(resolver.applicableCodeForItem(noTaxCodes, mockItem));
        assertNull(resolver.applicableCodeForItem(noTaxCodes, itemWithDates));
        assertNull(resolver.applicableCodeForItem(oneApplicableTaxCode, mockItem));
        assertNull(resolver.applicableCodeForItem(oneApplicableTaxCode, itemWithDates));
    }
}
