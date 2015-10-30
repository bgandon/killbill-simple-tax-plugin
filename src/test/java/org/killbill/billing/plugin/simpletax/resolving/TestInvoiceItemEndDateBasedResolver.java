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
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.math.BigDecimal;
import java.util.Set;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.internal.TaxCodeService;
import org.killbill.billing.test.helpers.TaxCodeBuilder;
import org.mockito.Mock;
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

    private static final DateTimeZone EUROPE_PARIS = DateTimeZone.forID("Europe/Paris");

    private final Set<TaxCode> noTaxCodes = ImmutableSet.of();

    private final LocalDate today = new LocalDate();
    private final LocalDate yesterday = today.minusDays(1);
    private final LocalDate tomorrow = today.plusDays(1);

    @Mock
    private SimpleTaxConfig cfg;
    @Mock
    private Account account;
    private Set<Invoice> allInvoices = ImmutableSet.of();
    private Function<InvoiceItem, BigDecimal> toAdjustedAmount = null;
    private Ordering<InvoiceItem> byAdjustedAmount = null;
    @Mock
    private TaxCodeService taxCodeService;

    private TaxCode tax0200JustToday = new TaxCodeBuilder()//
            .withName("JUST_TODAY")//
            .withTaxItemDescription("Just Today")//
            .withRate(new BigDecimal("0.200"))//
            .withStartingOn(today)//
            .withStoppingOn(tomorrow)//
            .build();
    private TaxCode tax0206BeforeYersterday = new TaxCodeBuilder()//
            .withName("BEFORE_YESTERDAY")//
            .withTaxItemDescription("Before Yesterday")//
            .withRate(new BigDecimal("0.206"))//
            .withStartingOn(null)//
            .withStoppingOn(yesterday)//
            .build();
    private TaxCode tax0196UntilYersterday = new TaxCodeBuilder()//
            .withName("UNTIL_YESTERDAY")//
            .withTaxItemDescription("Until Yesterday")//
            .withRate(new BigDecimal("0.196"))//
            .withStartingOn(null)//
            .withStoppingOn(today)//
            .build();
    private TaxCode tax0216FromYesterday = new TaxCodeBuilder()//
            .withName("FROM_YESTERDAY")//
            .withTaxItemDescription("From Yesterday")//
            .withRate(new BigDecimal("0.186"))//
            .withStartingOn(yesterday)//
            .withStoppingOn(null)//
            .build();
    private TaxCode tax0216FromTomorrow = new TaxCodeBuilder()//
            .withName("FROM_TOMORROW")//
            .withTaxItemDescription("From Tomorrow")//
            .withRate(new BigDecimal("0.216"))//
            .withStartingOn(tomorrow)//
            .withStoppingOn(null)//
            .build();
    private final Set<TaxCode> taxCodes = setOf(tax0196UntilYersterday, tax0200JustToday, tax0216FromTomorrow);

    @Mock
    private InvoiceItem itemWithNullDates;
    @Mock
    private InvoiceItem itemEndingToday;
    @Mock
    private InvoiceItem itemFromYesterdayToToday;
    @Mock
    private InvoiceItem itemStartingYesterday;

    private TaxResolver resolver;

    @BeforeClass
    public void init() {
        initMocks(this);
        resolver = resolverWithConfig(cfg);

        when(itemEndingToday.getEndDate()).thenReturn(today);

        when(itemFromYesterdayToToday.getStartDate()).thenReturn(yesterday);
        when(itemFromYesterdayToToday.getEndDate()).thenReturn(today);

        when(itemStartingYesterday.getStartDate()).thenReturn(yesterday);
    }

    private TaxResolver resolverWithConfig(SimpleTaxConfig cfg) {
        TaxComputationContext ctx = new TaxComputationContext(cfg, account, allInvoices, toAdjustedAmount,
                byAdjustedAmount, taxCodeService);
        return new InvoiceItemEndDateBasedResolver(ctx);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldThrowNPEOnNullInvoiceItem() {
        // Expect exception
        resolver.applicableCodeForItem(noTaxCodes, null);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldThrowNPEOnNullEndDateAndNullStartDate() {
        // Expect exception
        resolver.applicableCodeForItem(noTaxCodes, itemWithNullDates);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldThrowNPEOnNullTaxCodes() {
        // Expect exception
        resolver.applicableCodeForItem(null, itemEndingToday);
    }

    @Test(groups = "fast")
    public void shouldReturnNullForEmptyTaxCodes() {
        // Expect
        assertNull(resolver.applicableCodeForItem(noTaxCodes, itemEndingToday));
    }

    @Test(groups = "fast")
    public void shouldFavorUsingEndDateOverStartDate() {
        // When
        TaxCode tax = resolver.applicableCodeForItem(taxCodes, itemFromYesterdayToToday);

        // Then
        assertNotNull(tax);
        assertEquals(tax.getName(), "JUST_TODAY");
        assertEqualsIgnoreScale(tax.getRate(), new BigDecimal("0.2"));
    }

    @Test(groups = "fast")
    public void shouldUseStartDateWhenEndDateIsNull() {
        // When
        TaxCode tax = resolver.applicableCodeForItem(taxCodes, itemStartingYesterday);

        // Then
        assertNotNull(tax);
        assertEquals(tax.getName(), "UNTIL_YESTERDAY");
        assertEqualsIgnoreScale(tax.getRate(), new BigDecimal("0.196"));
    }

    @Test(groups = "fast")
    public void shouldInterpretEndDateInTaxationTimeZone() {
        // Given
        SimpleTaxConfig cfg = mock(SimpleTaxConfig.class);
        when(cfg.getTaxationTimeZone()).thenReturn(DateTimeZone.UTC);

        when(account.getTimeZone()).thenReturn(EUROPE_PARIS);
        TaxResolver resolver = resolverWithConfig(cfg);

        // When
        TaxCode tax = resolver.applicableCodeForItem(taxCodes, itemFromYesterdayToToday);

        // Then
        assertNotNull(tax);
        assertEquals(tax.getName(), "UNTIL_YESTERDAY");
        assertEqualsIgnoreScale(tax.getRate(), new BigDecimal("0.196"));
    }

    @Test(groups = "fast")
    public void shouldSelectEverGoingTaxes() {
        // Expect
        assertEquals(resolver.applicableCodeForItem(setOf(tax0216FromYesterday), itemEndingToday), tax0216FromYesterday);
    }

    @Test(groups = "fast")
    public void shouldIgnorePassedTaxes() {
        // Expect
        assertNull(resolver.applicableCodeForItem(setOf(tax0206BeforeYersterday), itemEndingToday));
    }

    @Test(groups = "fast")
    public void shouldIgnoreFutureTaxes() {
        // Expect
        assertNull(resolver.applicableCodeForItem(setOf(tax0216FromTomorrow), itemEndingToday));
    }

    @SafeVarargs
    private static <T> Set<T> setOf(T... elems) {
        return ImmutableSet.copyOf(elems);
    }
}
