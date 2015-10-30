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

import static org.killbill.billing.test.helpers.TestUtil.assertEqualsIgnoreScale;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.math.BigDecimal;
import java.util.Set;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.internal.TaxCodeService;
import org.killbill.billing.test.helpers.TaxCodeBuilder;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

/**
 * Tests for {@link InvoiceItemEndDateBasedResolver}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestInvoiceItemEndDateBasedResolver {

    private final Set<TaxCode> noTaxCodes = ImmutableSet.of();

    private final LocalDate today = new LocalDate();
    private final LocalDate yesterday = today.minusDays(1);

    private InvoiceItem itemWithEndDate;

    private TaxCode tax20PerCentFromToday;
    private TaxCode tax19PerCentUntilYersterday;

    private TaxResolver resolver;

    @BeforeClass
    public void init() {
        SimpleTaxConfig cfg = mock(SimpleTaxConfig.class);
        Account account = mock(Account.class);
        Set<Invoice> allInvoices = ImmutableSet.of();
        Function<InvoiceItem, BigDecimal> toAdjustedAmount = null;
        Ordering<InvoiceItem> byAdjustedAmount = null;
        TaxCodeService taxCodeService = mock(TaxCodeService.class);

        TaxComputationContext ctx = new TaxComputationContext(cfg, account, allInvoices, toAdjustedAmount,
                byAdjustedAmount, taxCodeService);
        resolver = new InvoiceItemEndDateBasedResolver(ctx);

        itemWithEndDate = mock(InvoiceItem.class);
        when(itemWithEndDate.getEndDate()).thenReturn(today);

        TaxCodeBuilder builder = new TaxCodeBuilder()//
                .withName("toto")//
                .withRate(new BigDecimal("0.200"))//
                .withTaxItemDescription("desc");
        tax20PerCentFromToday = builder//
                .withStartingOn(today)//
                .withStoppingOn(null)//
                .build();
        tax19PerCentUntilYersterday = builder//
                .withRate(new BigDecimal("0.196"))//
                .withStartingOn(null)//
                .withStoppingOn(today)//
                .build();
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldThrowNPEOnNullInvoiceItem() {
        // Expect exception
        resolver.applicableCodeForItem(noTaxCodes, null);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldThrowNPEOnNullEndDateAndNullStartDate() {
        // Expect exception
        resolver.applicableCodeForItem(noTaxCodes, mock(InvoiceItem.class));
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldThrowNPEOnNullTaxCodes() {
        // Expect exception
        resolver.applicableCodeForItem(null, itemWithEndDate);
    }

    @Test(groups = "fast")
    public void shouldReturnNullForEmptyTaxCodes() {
        // Expect
        assertNull(resolver.applicableCodeForItem(noTaxCodes, itemWithEndDate));
    }

    @Test(groups = "fast")
    public void shouldFavorUsingEndDateOverStartDate() {
        // Given
        Set<TaxCode> taxCodes = ImmutableSet.of(tax19PerCentUntilYersterday, tax20PerCentFromToday);

        InvoiceItem item = mock(InvoiceItem.class);
        LocalDate startDate = yesterday;
        when(item.getStartDate()).thenReturn(startDate);
        LocalDate endDate = today;
        when(item.getEndDate()).thenReturn(endDate);

        // When
        TaxCode tax = resolver.applicableCodeForItem(taxCodes, item);

        // Then
        assertNotNull(tax);
        assertEquals(tax.getName(), "toto");
        assertEqualsIgnoreScale(tax.getRate(), new BigDecimal("0.2"));
    }

    @Test(groups = "fast")
    public void shouldUseStartDateWhenEndDateIsNull() {
        // Given
        Set<TaxCode> taxCodes = ImmutableSet.of(tax19PerCentUntilYersterday, tax20PerCentFromToday);
        InvoiceItem item = mock(InvoiceItem.class);
        LocalDate startDate = yesterday;
        when(item.getStartDate()).thenReturn(startDate);

        // When
        TaxCode tax = resolver.applicableCodeForItem(taxCodes, item);

        // Then
        assertNotNull(tax);
        assertEquals(tax.getName(), "toto");
        assertEqualsIgnoreScale(tax.getRate(), new BigDecimal("0.196"));
    }
}
