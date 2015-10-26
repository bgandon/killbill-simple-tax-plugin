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

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.ZERO;
import static java.math.BigDecimal.valueOf;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.joda.time.DateTimeZone.forID;
import static org.joda.time.DateTimeZone.forOffsetHours;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.bigDecimal;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.convertTimeZone;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.integer;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.joinTaxCodes;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.localDate;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.resolverConstructor;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.splitTaxCodes;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.string;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.timeZone;
import static org.killbill.billing.test.helpers.Assert.assertEqualsIgnoreScale;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Map;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.resolving.InvoiceItemEndDateBasedResolver;
import org.killbill.billing.plugin.simpletax.resolving.NullTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.TaxResolver;
import org.killbill.billing.test.helpers.TaxCodeBuilder;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Tests for {@link ConvertionHelpers}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestConvertionHelpers {

    private static final String RATE = "rate";
    private static final BigDecimal TWO = valueOf(2L);
    private static final BigDecimal NINE = valueOf(9L);
    private static final ImmutableMap<String, String> EMPTY_CFG = ImmutableMap.<String, String> of();

    private static final String UTC_PLUS_ONE = "+01:00";
    private static final String EUROPE_PARIS = "Europe/Paris";
    private static final String EUROPE_LONDON = "Europe/London";
    private static final DateTimeZone UTC_OFFSET_ONE = forOffsetHours(1);
    private static final DateTimeZone PARIS = forID(EUROPE_PARIS);
    private static final DateTimeZone LONDON = forID(EUROPE_LONDON);

    private final LocalDate today = new LocalDate();
    private final LocalDate yesterday = today.minusDays(1);
    private final String nullClass = NullTaxResolver.class.getName();
    private final String endDateClass = InvoiceItemEndDateBasedResolver.class.getName();
    private Constructor<NullTaxResolver> nullTaxResolverCtor;
    private Constructor<InvoiceItemEndDateBasedResolver> endDateTaxResolverCtor;

    @BeforeClass
    public void init() throws Exception {
        nullTaxResolverCtor = NullTaxResolver.class.getConstructor(TaxComputationContext.class);
        endDateTaxResolverCtor = InvoiceItemEndDateBasedResolver.class.getConstructor(TaxComputationContext.class);
    }

    private static Map<String, String> cfgOf(String key, String val) {
        return ImmutableMap.of(key, val);
    }

    @Test(groups = "fast", expectedExceptions = InstantiationException.class)
    public void shouldBeAbstractClass() throws Exception {
        ConvertionHelpers.class.getConstructor().newInstance();
    }

    @Test(groups = "fast")
    public void shouldReturnDefaultBigDecimal() {
        // Expect
        assertNull(bigDecimal(EMPTY_CFG, "plop", null));
        assertEqualsIgnoreScale(bigDecimal(EMPTY_CFG, "plip", ONE), ONE);

        assertNull(bigDecimal(cfgOf(RATE, EMPTY), RATE, null));
        assertEqualsIgnoreScale(bigDecimal(cfgOf(RATE, EMPTY), RATE, TEN), TEN);
        assertEqualsIgnoreScale(bigDecimal(cfgOf(RATE, " \t\n\f\r"), RATE, TWO), TWO);
        assertEqualsIgnoreScale(bigDecimal(cfgOf(RATE, " "), RATE, ONE), ONE);

        assertNull(bigDecimal(cfgOf(RATE, "boom"), RATE, null));
        assertEqualsIgnoreScale(bigDecimal(cfgOf(RATE, "boom"), RATE, ZERO), ZERO);
        assertEqualsIgnoreScale(bigDecimal(cfgOf(RATE, "boom"), RATE, NINE), NINE);
    }

    @Test(groups = "fast")
    public void shouldConvertBigDecimalWithTrimming() {
        // Given
        Map<String, String> cfg = cfgOf(RATE, "2.00");

        // Expect
        assertEqualsIgnoreScale(bigDecimal(cfg, RATE, ONE), TWO);
        assertEqualsIgnoreScale(bigDecimal(cfg, RATE, null), TWO);
        assertEqualsIgnoreScale(bigDecimal(cfgOf(RATE, "\t2.0 "), RATE, null), TWO);
    }

    @Test(groups = "fast")
    public void shouldReturnDefaultInteger() {
        // Expect
        assertEquals(integer(EMPTY_CFG, "plip", 1), 1);

        assertEquals(integer(cfgOf(RATE, EMPTY), RATE, 10), 10);
        assertEquals(integer(cfgOf(RATE, " \t\r\n\f"), RATE, 2), 2);

        assertEquals(integer(cfgOf(RATE, "boom!"), RATE, 0), 0);
        assertEquals(integer(cfgOf(RATE, "boom!"), RATE, 9), 9);

        assertEquals(integer(cfgOf(RATE, "2.0"), RATE, 9), 9);
        assertEquals(integer(cfgOf(RATE, "2E1"), RATE, 9), 9);
        assertEquals(integer(cfgOf(RATE, "0x2"), RATE, 9), 9);
    }

    @Test(groups = "fast")
    public void shouldConvertIntegerWithTrimming() {
        // Expect
        assertEquals(integer(cfgOf(RATE, "2"), RATE, 1), 2);
        assertEquals(integer(cfgOf(RATE, "3"), RATE, -1), 3);
        assertEquals(integer(cfgOf(RATE, "-3"), RATE, 10), -3);
        assertEquals(integer(cfgOf(RATE, "010"), RATE, 0), 10);
        assertEquals(integer(cfgOf(RATE, "\t42 "), RATE, 0), 42);
    }

    @Test(groups = "fast")
    public void shouldReturnDefaultString() {
        // Expect
        assertEquals(string(EMPTY_CFG, "plip", "plop"), "plop");
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotAcceptNullDefaultString() {
        // Expect exception
        string(EMPTY_CFG, "plip", null);
    }

    @Test(groups = "fast")
    public void shouldFindExactString() {
        // Expect
        assertEquals(string(cfgOf("toto", "titi"), "toto", "plop"), "titi");
        assertEquals(string(cfgOf("toto", "\ttiti\t"), "toto", "plop"), "\ttiti\t");
    }

    @Test(groups = "fast")
    public void shouldReturnDefaultLocalDate() {
        // Expect
        assertEquals(localDate(EMPTY_CFG, "plip", today), today);
        assertEquals(localDate(cfgOf("date", "\t\n\f\r "), "date", today), today);
        assertEquals(localDate(cfgOf("date", "boom!"), "date", today), today);
        assertEquals(localDate(cfgOf("date", "2015-10-25 boom!"), "date", today), today);
    }

    @Test(groups = "fast")
    public void shouldConvertLocalDateWithTrimming() {
        // Expect
        assertEquals(localDate(cfgOf("date", "2015-10-25"), "date", today), new LocalDate("2015-10-25"));
        assertEquals(localDate(cfgOf("date", "\t2015-10-25\t"), "date", today), new LocalDate("2015-10-25"));
    }

    @Test(groups = "fast")
    public void shouldReturnConstructorForNullResolver() {
        // Given
        String pkg = "org.killbill.billing.plugin.simpletax";

        // Expect
        assertNull(resolverConstructor(EMPTY_CFG, "plop", null));

        assertEquals(resolverConstructor(EMPTY_CFG, "plop", nullTaxResolverCtor), nullTaxResolverCtor);
        assertEquals(resolverConstructor(cfgOf("plop", "boom!"), "plop", nullTaxResolverCtor), nullTaxResolverCtor);
        assertEquals(resolverConstructor(cfgOf("plop", pkg + ".Boom!"), "plop", nullTaxResolverCtor),
                nullTaxResolverCtor);
        assertEquals(resolverConstructor(cfgOf("plop", "\t\n\f\r "), "plop", nullTaxResolverCtor), nullTaxResolverCtor);

        assertEquals(resolverConstructor(cfgOf("plop", this.getClass().getName()), "plop", nullTaxResolverCtor),
                nullTaxResolverCtor);
        assertEquals(resolverConstructor(cfgOf("plop", TaxResolver.class.getName()), "plop", nullTaxResolverCtor),
                nullTaxResolverCtor);
    }

    @Test(groups = "fast")
    public void shouldReturnConstructorResolverWithTrimming() {
        // Expect
        assertEquals(resolverConstructor(cfgOf("resolver", nullClass), "resolver", null), nullTaxResolverCtor);
        assertEquals(resolverConstructor(cfgOf("resolver", "\t" + nullClass + ' '), "resolver", null),
                nullTaxResolverCtor);

        assertEquals(resolverConstructor(cfgOf("resolver", endDateClass), "resolver", null), endDateTaxResolverCtor);
        assertEquals(resolverConstructor(cfgOf("resolver", " " + endDateClass + '\t'), "resolver", null),
                endDateTaxResolverCtor);
    }

    @Test(groups = "fast")
    public void shouldReturnDefaultTimeZone() {
        // Expect
        assertNull(timeZone(EMPTY_CFG, "plop", null));
        assertEquals(timeZone(EMPTY_CFG, "plop", PARIS), PARIS);
        assertEquals(timeZone(EMPTY_CFG, "plop", LONDON), LONDON);

        assertEquals(timeZone(cfgOf("tz", EUROPE_PARIS + " boom!"), "tz", null), null);
        assertEquals(timeZone(cfgOf("tz", EUROPE_PARIS + " boom!"), "tz", LONDON), LONDON);
    }

    @Test(groups = "fast")
    public void shouldConvertTimeZoneWithTrimming() {
        // Expect
        assertEquals(timeZone(cfgOf("tz", EUROPE_PARIS), "tz", null), PARIS);
        assertEquals(timeZone(cfgOf("tz", "\t" + EUROPE_PARIS + ' '), "tz", LONDON), PARIS);

        assertEquals(timeZone(cfgOf("tz", UTC_PLUS_ONE), "tz", null), UTC_OFFSET_ONE);
        assertEquals(timeZone(cfgOf("tz", "\t" + UTC_PLUS_ONE + ' '), "tz", LONDON), UTC_OFFSET_ONE);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotSpliButFail() {
        splitTaxCodes(null);
    }

    @Test(groups = "fast")
    public void shouldReturnEmptySet() {
        ImmutableSet<Object> emptySet = ImmutableSet.of();

        assertEquals(splitTaxCodes(EMPTY), emptySet);
        assertEquals(splitTaxCodes(",,,,\t\n\f\r ,,"), emptySet);
    }

    @Test(groups = "fast")
    public void shouldSplitTaxCodesIgnoringDuplicatesAndPreservingOrder() {
        ImmutableSet<String> totoTiti = ImmutableSet.of("toto", "titi");
        assertEquals(splitTaxCodes(",\n,toto\t, \ftiti\r,"), totoTiti);

        assertEquals(splitTaxCodes("toto, titi, toto, titi"), totoTiti);

        ImmutableSet<String> titiToto = ImmutableSet.of("titi", "toto");
        assertEquals(splitTaxCodes("titi, toto"), titiToto);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotJointButFail() {
        joinTaxCodes(null);
    }

    @Test(groups = "fast")
    public void shouldJoinTaxCodes() {
        // Given
        TaxCode toto = new TaxCodeBuilder().withName("toto").build();
        TaxCode titi = new TaxCodeBuilder().withName("titi").build();

        // Expect
        assertEquals(joinTaxCodes(ImmutableSet.of(toto, titi)), "toto, titi");
        assertEquals(joinTaxCodes(ImmutableSet.of(titi, toto)), "titi, toto");
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotConvertButFailForNullDate() {
        convertTimeZone(null, PARIS, LONDON);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotConvertButFailForNullOriginTZ() {
        convertTimeZone(today, null, LONDON);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotConvertButFailNullTargetTZ() {
        convertTimeZone(today, PARIS, null);
    }

    @Test(groups = "fast")
    public void shouldConvertTimeZone() {
        assertEquals(convertTimeZone(today, PARIS, LONDON), yesterday);
        assertEquals(convertTimeZone(today, LONDON, LONDON), today);
    }
}
