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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.primitives.Ints.tryParse;
import static java.lang.Thread.currentThread;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.split;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.joda.time.format.ISODateTimeFormat.localDateParser;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.DateTimeParser;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.internal.Country;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.resolving.TaxResolver;

import com.google.common.collect.ImmutableSet;

/**
 * Utility methods that:
 * <ul>
 * <li>Convert strings from configuration properties into objects with defaults</li>
 * <li>Convert comma-separated tax codes to sets of tax code definitions, back
 * and forth</li>
 * <li>Convert a local date from a time zone to another time zone, with
 * assumptions</li>
 * </ul>
 *
 * @author Benjamin Gandon
 */
public final class ConvertionHelpers {
    private ConvertionHelpers() {
    }

    /**
     * Constructs a {@link BigDecimal} instance from a configuration property,
     * or return a default value when the property is blank or inexistent.
     *
     * @param cfg
     *            The plugin configuration properties.
     * @param propName
     *            The property name.
     * @param defaultValue
     *            The default value.
     * @return A new {@link BigDecimal} instance reflecting the designated
     *         configuration property, or the given default value.
     * @throws NullPointerException
     *             When {@code cfg} is {@code null}.
     */
    static BigDecimal bigDecimal(Map<String, String> cfg, String propName, BigDecimal defaultValue) {
        String strValue = cfg.get(propName);
        if (isBlank(strValue)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(trim(strValue));
        } catch (NumberFormatException exc) {
            return defaultValue;
        }
    }

    /**
     * Returns a non-{@code null} {@link Integer} value from a configuration
     * property, or return a default value when the property is blank or
     * inexistent.
     *
     * @param cfg
     *            The plugin configuration properties.
     * @param propName
     *            The property name.
     * @param defaultValue
     *            The default value.
     * @return A non-{@code null} integer value reflecting the designated
     *         configuration property, or the given default value.
     * @throws NullPointerException
     *             When {@code cfg} is {@code null}.
     */
    static int integer(Map<String, String> cfg, String propName, int defaultValue) {
        String strValue = cfg.get(propName);
        if (isBlank(strValue)) {
            return defaultValue;
        }
        Integer convertedValue = tryParse(trim(strValue));
        return firstNonNull(convertedValue, defaultValue);
    }

    /**
     * Returns a non-{@code null} {@link String} from a configuration property,
     * or return a default value when the property is blank or inexistent.
     *
     * @param cfg
     *            The plugin configuration properties.
     * @param propName
     *            The property name.
     * @param defaultValue
     *            The default value. Must not be {@code null}.
     * @return A string reflecting the designated configuration property, or the
     *         given default value.
     * @throws NullPointerException
     *             When {@code cfg} or {@code defaultValue} are {@code null}.
     */
    static String string(Map<String, String> cfg, String propName, String defaultValue) {
        return firstNonNull(cfg.get(propName), defaultValue);
    }

    /**
     * Constructs a {@link LocalDate} instance from a configuration property, or
     * return a default value when the property is blank or inexistent.
     *
     * @param cfg
     *            The plugin configuration properties.
     * @param propName
     *            The property name.
     * @param defaultValue
     *            The default value.
     * @return A new {@link LocalDate} instance reflecting the designated
     *         configuration property, or the given default value.
     * @throws NullPointerException
     *             When {@code cfg} is {@code null}.
     */
    static LocalDate localDate(Map<String, String> cfg, String propName, LocalDate defaultValue) {
        String date = cfg.get(propName);
        if (isBlank(date)) {
            return defaultValue;
        }
        try {
            return localDateParser().parseLocalDate(trim(date));
        } catch (IllegalArgumentException exc) {
            return defaultValue;
        }
    }

    /**
     * Loads a class from a configuration property and returns its constructor
     * taking a single {@link TaxComputationContext} argument, or return a
     * default value when the property is blank or inexistent.
     *
     * @param cfg
     *            The plugin configuration properties.
     * @param propName
     *            The property name.
     * @param defaultValue
     *            The default value.
     * @return A new {@link Constructor} instance reflecting the designated
     *         configuration property, or the given default value.
     * @throws NullPointerException
     *             When {@code cfg} is {@code null}.
     */
    static Constructor<? extends TaxResolver> resolverConstructor(Map<String, String> cfg, String propName,
            Constructor<? extends TaxResolver> defaultValue) {
        String className = cfg.get(propName);
        if (isBlank(className)) {
            return defaultValue;
        }
        ClassLoader loader = currentThread().getContextClassLoader();
        Class<?> clazz;
        try {
            clazz = loader.loadClass(trim(className));
        } catch (ClassNotFoundException exc) {
            return defaultValue;
        }
        if (!TaxResolver.class.isAssignableFrom(clazz)) {
            return defaultValue;
        }

        Constructor<?> constructor;
        try {
            constructor = clazz.getConstructor(TaxComputationContext.class);
        } catch (NoSuchMethodException exc) {
            return defaultValue;
        }
        return asConstructorOfTaxResolver(constructor);
    }

    /**
     * The point here is just to reduce the amount of code in which we suppress
     * {@code "unchecked"} warnings with {@code @SuppressWarnings("unchecked")}.
     */
    @SuppressWarnings("unchecked")
    private static Constructor<? extends TaxResolver> asConstructorOfTaxResolver(Constructor<?> constructor) {
        return (Constructor<TaxResolver>) constructor;
    }

    /**
     * Thread-safe time zone parser.
     *
     * <pre>
     * time-zone     = tz-identifier | offset
     * tz-identifier = ID [A-Z][a-z]+/[A-Z][a-z]+
     * offset        = 'Z' | (('+' | '-') HH [':' mm [':' ss [('.' | ',') SSS]]])
     * </pre>
     * <p>
     * Specifying a time zone identifier like {@code Europe/Paris} is always
     * preferable.
     *
     * @see DateTimeZone#getAvailableIDs()
     */
    private static final DateTimeFormatter TIME_ZONE_PARSER = new DateTimeFormatterBuilder()
            .append(null,
                    new DateTimeParser[] { new DateTimeFormatterBuilder().appendTimeZoneId().toParser(),
                            new DateTimeFormatterBuilder().appendTimeZoneOffset("Z", true, 2, 4).toParser() })
            .toFormatter().withOffsetParsed();

    /**
     * Constructs a {@link DateTimeZone} instance from a configuration property,
     * or return a default value when the property is blank or inexistent.
     *
     * @param cfg
     *            The plugin configuration properties.
     * @param propName
     *            The property name.
     * @param defaultTimeZone
     *            The default value.
     * @return A new {@link DateTimeZone} instance reflecting the designated
     *         configuration property, or the given default value.
     * @throws NullPointerException
     *             When {@code cfg} is {@code null}.
     */
    static DateTimeZone timeZone(Map<String, String> cfg, String propName, DateTimeZone defaultTimeZone) {
        String timeZone = cfg.get(propName);
        if (isBlank(timeZone)) {
            return defaultTimeZone;
        }
        try {
            return TIME_ZONE_PARSER.parseDateTime(trim(timeZone)).getZone();
        } catch (IllegalArgumentException e) {
            return defaultTimeZone;
        }
    }

    /**
     * Constructs a new {@link Country} instance from a configuration property,
     * or return a default value when the property is blank or inexistent.
     *
     * @param cfg
     *            The plugin configuration properties.
     * @param propName
     *            The property name.
     * @param defaultCountry
     *            The default value.
     * @return A new {@link Country} instance reflecting the designated
     *         configuration property, or the given default value.
     * @throws NullPointerException
     *             When {@code cfg} is {@code null}.
     */
    static Country country(Map<String, String> cfg, String propName, Country defaultCountry) {
        String countryCode = cfg.get(propName);
        if (isBlank(countryCode)) {
            return defaultCountry;
        }
        try {
            return new Country(trim(countryCode));
        } catch (IllegalArgumentException e) {
            return defaultCountry;
        }
    }

    private static final String TAX_CODES_JOIN_SEPARATOR = ", ";
    private static final String TAX_CODES_SPLIT_SEPARATORS = TAX_CODES_JOIN_SEPARATOR + "\t\n\f\r";

    /**
     * Converts tax codes to their definitions, preserving the order in which
     * the are specified.
     *
     * @param names
     *            The comma-separated list of tax codes, identified by their
     *            names. Must not be {@code null}.
     * @return The set of tax codes names, without any duplicates, in the order
     *         they were listed.
     * @throws NullPointerException
     *             when {@code names} is {@code null}.
     */
    @Nonnull
    public static Set<String> splitTaxCodes(@Nonnull String names) {
        ImmutableSet.Builder<String> taxCodes = ImmutableSet.builder();
        for (String name : split(names, TAX_CODES_SPLIT_SEPARATORS)) {
            taxCodes.add(name);
        }
        return taxCodes.build();
    }

    /**
     * Converts tax code definitions into a comma-separated list of tax codes,
     * preserving the order in which they are iterated.
     *
     * @param taxCodes
     *            The set of tax codes to convert.
     * @return A comma-separated list of tax codes.
     * @throws NullPointerException
     *             when {@code taxCodes} is {@code null}, or when any element in
     *             {@code taxCodes} is {@code null}.
     */
    @Nonnull
    public static String joinTaxCodes(@Nonnull Iterable<TaxCode> taxCodes) {
        StringBuilder csv = new StringBuilder();
        for (TaxCode code : taxCodes) {
            if (csv.length() > 0) {
                csv.append(TAX_CODES_JOIN_SEPARATOR);
            }
            csv.append(code.getName());
        }
        return csv.toString();
    }

    /**
     * Converts a local date from an “origin” time zone to a “target” time zone,
     * assuming that the date actually refers to the first instant of the
     * designated day, in the “origin” time zone.
     * <p>
     * This is useful to know which day it was in “target” time zone when the
     * designated day started in the “origin” time zone.
     *
     * @param localDate
     *            A date specification, to be interpreted in the
     *            {@code fromTimeZone}. Never {@code null}.
     * @param originTimeZone
     *            The zone in which the partial date should be “interpreted”.
     *            Never {@code null}.
     * @param targetTimeZone
     *            The zone in which the first instant of the day is requested.
     *            Never {@code null}.
     * @return The date in {@code toTimeZone} for the first instant of the day
     *         {@code localDate} in the time zone {@code fromTimeZone}
     */
    public static LocalDate convertTimeZone(LocalDate localDate, DateTimeZone originTimeZone,
            DateTimeZone targetTimeZone) {
        checkNotNull(originTimeZone);
        checkNotNull(targetTimeZone);
        return localDate.toDateTimeAtStartOfDay(originTimeZone).withZone(targetTimeZone).toLocalDate();
    }

    /** An exact regular expression to match UUIDs. */
    public static final String UUID_EXACT_PATTERN = "(?i:[a-f\\d]{8}(?:-[a-f\\d]{4}){3}-[a-f\\d]{12})";
    /**
     * A loose regular expression to match canonical UUID representations that
     * will be properly converted by {@link UUID#fromString(String)}.
     */
    public static final String UUID_LOOSE_PATTERN = "\\w{8}(?:-\\w{4}){3}-\\w{12}";

    /**
     * Converts a string into a {@link UUID}.
     * <p>
     * <strong>WARNING:</strong>
     * <p>
     * Due to how the underlying {@link UUID#fromString(String)} implementation,
     * this method silently accepts non-canonical representations of UUIDs. For
     * example, the malformed
     * {@code 43210000-23456789-ABCD-0000-7FFF123456789ABC} representation is
     * converted to {@code 43212345-6789-abcd-7fff-123456789abc}.
     * <p>
     * These multiple representations of a single UUID could cause issues if any
     * security system assumes that only single canonical representations are
     * used. Thus, you should first make sure that the UUID conforms either to
     * {@link #UUID_EXACT_PATTERN} or {@link #UUID_LOOSE_PATTERN}.
     *
     * @param name
     *            A valid string representation of a UUID.
     * @return A UUID instance or {@code null}.
     */
    public static UUID toUUIDOrNull(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException exc) {
            return null;
        }
    }
}
