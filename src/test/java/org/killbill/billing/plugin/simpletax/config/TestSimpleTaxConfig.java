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
package org.killbill.billing.plugin.simpletax.config;

import static java.math.BigDecimal.ZERO;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig.DEFAULT_TAX_ITEM_DESC;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.osgi.service.log.LogService.LOG_ERROR;
import static org.osgi.service.log.LogService.LOG_WARNING;
import static org.testng.Assert.assertEquals;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTimeZone;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.resolving.InvoiceItemEndDateBasedResolver;
import org.killbill.billing.plugin.simpletax.resolving.NullTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.TaxResolver;
import org.killbill.billing.plugin.simpletax.util.LazyValue;
import org.killbill.billing.test.helpers.TaxCodeBuilder;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;

/**
 * Tests for {@link SimpleTaxConfig}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestSimpleTaxConfig {

    private static final String TAX_RESOLVER_PROP = "org.killbill.billing.plugin.simpletax.taxResolver";

    private static final LazyValue<Constructor<NullTaxResolver>, RuntimeException> NTR_CONSTRUCTOR = new LazyValue<Constructor<NullTaxResolver>, RuntimeException>() {
        @Override
        protected Constructor<NullTaxResolver> initialize() throws RuntimeException {
            try {
                return NullTaxResolver.class.getConstructor(TaxComputationContext.class);
            } catch (NoSuchMethodException exc) {
                throw new RuntimeException(exc);
            }
        }
    };

    private static final ImmutableMap<String, String> WITH_NON_EXISTING_TAX_RESOLVER = ImmutableMap.of(
            TAX_RESOLVER_PROP, "bad.package.NullTaxResolver");
    private static final ImmutableMap<String, String> WITH_INVALID_TAX_RSOLVER_SUBTYPE = ImmutableMap.of(
            TAX_RESOLVER_PROP, TestSimpleTaxConfig.class.getName());
    private static final ImmutableMap<String, String> WITH_INVALID_TAX_RESOLVER_CONSTRUCTOR = ImmutableMap.of(
            TAX_RESOLVER_PROP, InvalidTaxResolver.class.getName());
    private static final Map<String, String> WITH_NOOP_TAX_RESOLVER = ImmutableMap.of(TAX_RESOLVER_PROP,
            NullTaxResolver.class.getName());

    private static final Map<String, String> WITH_TAX_CODE_A = ImmutableMap.of(
            "org.killbill.billing.plugin.simpletax.taxCodes.taxA.rate", "0.10");
    private static final Map<String, String> WITH_TAX_CODE_B = ImmutableMap.of(
            "org.killbill.billing.plugin.simpletax.taxCodes.taxB", "");
    private static final Map<String, String> WITH_TAX_CODE_C = ImmutableMap.of(
            "org.killbill.billing.plugin.simpletax.taxCodes.taxC.rate", "0.200",
            "org.killbill.billing.plugin.simpletax.taxCodes.taxC.taxItem.description", "Tax C");
    private static final ImmutableMap<String, String> WITH_PRODUCT_A = ImmutableMap.of(
            "org.killbill.billing.plugin.simpletax.products.productA", "plop, taxA");
    private static final ImmutableMap<String, String> WITH_PRODUCT_B = ImmutableMap.of(
            "org.killbill.billing.plugin.simpletax.products.productB", "taxA");

    private static final TaxCode TAX_A = new TaxCodeBuilder()//
            .withName("taxA")//
            .withRate(new BigDecimal("0.1"))//
            .withTaxItemDescription(DEFAULT_TAX_ITEM_DESC)//
            .build();
    private static final TaxCode TAX_B = new TaxCodeBuilder()//
            .withName("taxB")//
            .withRate(ZERO)//
            .withTaxItemDescription(DEFAULT_TAX_ITEM_DESC)//
            .build();
    private static final TaxCode TAX_C = new TaxCodeBuilder()//
            .withName("taxC")//
            .withRate(new BigDecimal("0.2"))//
            .withTaxItemDescription("Tax C")//
            .build();

    private static class InvalidTaxResolver implements TaxResolver {
        @Override
        public TaxCode applicableCodeForItem(Set<TaxCode> taxCodes, InvoiceItem item) {
            return null;
        }
    }

    @Mock
    private OSGIKillbillLogService logService;

    @BeforeMethod
    public void init() {
        initMocks(this);
    }

    @Test(groups = "fast")
    public void shouldEarlyWarnOnMissingtaxResolver() {
        // When
        SimpleTaxConfig config = new SimpleTaxConfig(ImmutableMap.<String, String> of(), logService);

        // Then
        verify(logService).log(eq(LOG_WARNING),
                argThat(allOf(containsString(TAX_RESOLVER_PROP), containsString("should not be blank"))));
        verifyNoMoreInteractions(logService);
        assertEquals(config.getTaxResolverConstructor(), NTR_CONSTRUCTOR.get());
    }

    @Test(groups = "fast")
    public void shouldEarlyComplainOnNonExistingTaxResolverClass() {
        // When
        SimpleTaxConfig config = new SimpleTaxConfig(WITH_NON_EXISTING_TAX_RESOLVER, logService);

        // Then
        verify(logService).log(eq(LOG_ERROR),
                argThat(allOf(containsStringIgnoringCase("cannot load class"), containsString(TAX_RESOLVER_PROP))));
        verifyNoMoreInteractions(logService);
        assertEquals(config.getTaxResolverConstructor(), NTR_CONSTRUCTOR.get());
    }

    @Test(groups = "fast")
    public void shouldEarlyComplainOnNonTaxResolverType() {
        // Given
        Map<String, String> cfg = WITH_INVALID_TAX_RSOLVER_SUBTYPE;

        // When
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);

        // Then
        InOrder inOrder = inOrder(logService);
        inOrder.verify(logService).log(
                eq(LOG_ERROR),
                argThat(allOf(containsStringIgnoringCase("invalid class"), containsString("sub-class"),
                        containsString(TAX_RESOLVER_PROP))));
        inOrder.verify(logService).log(
                eq(LOG_ERROR),
                argThat(allOf(containsStringIgnoringCase("invalid class"), containsString("constructor"),
                        containsString(TAX_RESOLVER_PROP))));
        inOrder.verifyNoMoreInteractions();
        assertEquals(config.getTaxResolverConstructor(), NTR_CONSTRUCTOR.get());
    }

    @Test(groups = "fast")
    public void shouldEarlyComplainOnNonExistingTTCConstructor() {
        // Given
        Map<String, String> cfg = WITH_INVALID_TAX_RESOLVER_CONSTRUCTOR;

        // When
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);

        // Then
        verify(logService).log(
                eq(LOG_ERROR),
                argThat(allOf(containsStringIgnoringCase("invalid class"), containsString("constructor"),
                        containsString(TAX_RESOLVER_PROP))));
        verifyNoMoreInteractions(logService);
        assertEquals(config.getTaxResolverConstructor(), NTR_CONSTRUCTOR.get());
    }

    @Test(groups = "fast")
    public void shouldEarlyComplainOnNonExistingTaxCodes() {
        // Given
        Map<String, String> cfg = cfgBuilder()//
                .putAll(WITH_TAX_CODE_A)//
                .putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_PRODUCT_A)//
                .build();

        // When
        new SimpleTaxConfig(cfg, logService);

        // Then
        verify(logService).log(
                eq(LOG_ERROR),
                argThat(allOf(containsString("org.killbill.billing.plugin.simpletax.products.productA"),
                        containsString("tax code [plop] is not defined"))));
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldDefineTaxCode() {
        // Given
        Map<String, String> cfg = cfgBuilder()//
                .putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_TAX_CODE_C)//
                .build();

        // When
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);

        // Then
        assertEquals(config.findTaxCode("taxC"), TAX_C);
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldDefineTaxCodeWithNameAndAllDefaultValues() {
        // Given
        Map<String, String> cfg = cfgBuilder()//
                .putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_TAX_CODE_B)//
                .build();

        // When
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);

        // Then
        assertEquals(config.findTaxCode("taxB"), TAX_B);
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldDefineTaxationTimeZone() {
        // Given
        Map<String, String> cfg = cfgBuilder()//
                .putAll(WITH_NOOP_TAX_RESOLVER)//
                .put("org.killbill.billing.plugin.simpletax.taxationTimeZone", "Europe/Paris")//
                .build();
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);

        // Expect
        assertEquals(config.getTaxationTimeZone(), DateTimeZone.forID("Europe/Paris"));
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldDefineTaxAmountPrecision() {
        // Given
        Map<String, String> cfg = cfgBuilder()//
                .putAll(WITH_NOOP_TAX_RESOLVER)//
                .put("org.killbill.billing.plugin.simpletax.taxItem.amount.precision", "7")//
                .build();
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);

        // Expect
        assertEquals(config.getTaxAmountPrecision(), 7);
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldReturnTaxResolverConstructor() throws Exception {
        // Given
        Map<String, String> cfg = ImmutableMap.of(TAX_RESOLVER_PROP, InvoiceItemEndDateBasedResolver.class.getName());
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);

        // When
        Constructor<? extends TaxResolver> constructor = config.getTaxResolverConstructor();

        // Then
        assertEquals(constructor, InvoiceItemEndDateBasedResolver.class.getConstructor(TaxComputationContext.class));
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldReturnedEmptyConfiguredTaxCode() {
        // Given
        Map<String, String> cfg = cfgBuilder().putAll(WITH_NOOP_TAX_RESOLVER)//
                .build();
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);

        // When
        Set<TaxCode> taxCodes = config.getConfiguredTaxCodes("non-existing-product");

        // Then
        assertEquals(taxCodes, ImmutableSet.of());
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldReturnedConfiguredTaxCode() {
        // Given
        Map<String, String> cfg = cfgBuilder().putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_TAX_CODE_A)//
                .putAll(WITH_PRODUCT_B)//
                .build();
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);

        // When
        Set<TaxCode> taxCodes = config.getConfiguredTaxCodes("productB");

        // Then
        assertEquals(taxCodes, ImmutableSet.of(TAX_A));
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldReturnedConfiguredTaxCodeAndComplainForUndefinedTaxCode() {
        // Given
        Map<String, String> cfg = cfgBuilder().putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_TAX_CODE_A)//
                .putAll(WITH_PRODUCT_A)//
                .build();
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);
        reset(logService);

        // When
        Set<TaxCode> taxCodes = config.getConfiguredTaxCodes("productA");

        // Then
        assertEquals(taxCodes, ImmutableSet.of(TAX_A));
        verify(logService).log(eq(LOG_ERROR), argThat(allOf(containsString("plop"), containsString("is undefined"))));
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldReturnedTaxCodesOrComplain() {
        // Given
        Map<String, String> cfg = cfgBuilder().putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_TAX_CODE_A)//
                .build();
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logService);
        reset(logService);

        // When
        Set<TaxCode> taxCodes = config.findTaxCodes("taxA, bim", "from plop");

        // Then
        assertEquals(taxCodes, ImmutableSet.of(TAX_A));
        verify(logService).log(eq(LOG_ERROR),
                argThat(allOf(containsString("bim"), containsString("from plop"), containsString("is undefined"))));
        verifyNoMoreInteractions(logService);
    }

    private static Builder<String, String> cfgBuilder() {
        return ImmutableMap.<String, String> builder();
    }
}
