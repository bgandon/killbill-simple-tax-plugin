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

import static com.google.common.collect.Sets.newLinkedHashSet;
import static java.lang.Thread.currentThread;
import static org.apache.commons.lang3.StringUtils.INDEX_NOT_FOUND;
import static org.apache.commons.lang3.StringUtils.indexOf;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.bigDecimal;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.integer;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.localDate;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.resolverConstructor;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.splitTaxCodes;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.string;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.timeZone;
import static org.osgi.service.log.LogService.LOG_ERROR;
import static org.osgi.service.log.LogService.LOG_WARNING;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.resolving.NullTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.TaxResolver;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * A configuration accessor for the simple-tax plugin.
 * <p>
 * The common prefix for configuration properties is {@value #PROPERTY_PREFIX}.
 * <p>
 * <strong>Mandatory configuration properties</strong> <blockquote>
 * <dl>
 * <dt>{@value #TAX_RESOLVER_PROPERTY}</dt>
 * <dd>The {@link TaxResolver} implementation to use.</dd>
 * <dt>{@value #TAX_AMOUNT_PRECISION_PROPERTY}</dt>
 * <dd>The scale to apply when computing amounts of tax items in invoices. This
 * usually depends on the currency that is used, but here we keep it simple.
 * It's just a per-tenant scale.</dd>
 * <dt>{@value #PRODUCT_TAX_CODE_PREFIX} &lt;product-name&gt;</dt>
 * <dd>A comma-separated (or space-separated) list of tax codes that apply to a
 * certain products. Several tax codes can be configured for one product because
 * they can specify different rates for different date ranges. It's up to the
 * {@link TaxResolver} implementation to choose which one applies to a certain
 * invoice item whose product matches the {@code product-name} here.</dd>
 * <dt>{@value #TAX_CODES_PREFIX} &lt;tax-code-name&gt; {@value #RATE_SUFFIX}</dt>
 * <dd>The rate to apply when applying this tax code. Please not that a 20.0%
 * rate is actually 0.200 here. The given value is converted as a
 * {@link BigDecimal}, so any fractional zeroes to the right <em>do</em> make a
 * difference in the resulting {@linkplain BigDecimal#scale() scale}.</dd>
 * <dt>{@value #TAX_CODES_PREFIX} &lt;tax-code-name&gt;
 * {@value #TAX_ITEM_DESCRIPTION_SUFFIX}</dt>
 * <dd>The description for tax items. Localization of this description is not
 * possible here, but can be implemented with custom
 * {@linkplain org.killbill.billing.invoice.api.formatters.InvoiceFormatter
 * invoice formatters}.</dd>
 * <dt>{@value #TAX_CODES_PREFIX} &lt;tax-code-name&gt;
 * {@value #STARTING_ON_SUFFIX}</dt>
 * <dd>The first day on which the tax code <em>starts</em> being applicable.</dd>
 * <dt>{@value #TAX_CODES_PREFIX} &lt;tax-code-name&gt;
 * {@value #STOPPING_ON_SUFFIX}</dt>
 * <dd>The first day on which this tax code <em>ceases</em> to be applicable.</dd>
 * </dl>
 * </blockquote>
 * <p>
 * <strong>Optional configuration properties</strong> <blockquote>
 * <dl>
 * <dt>{@value #TAXATION_TIME_ZONE_PROPERTY}</dt>
 * <dd>The time zone to consider when using dates to apply taxes. It is up to
 * the {@link TaxResolver} implementation to use this property or not.</dd>
 * </dl>
 * </blockquote>
 * <p>
 * <strong>Notes on tax codes:</strong>
 * <ol>
 * <li>Tax codes are uniquely identified by their names.</li>
 * <li>Tax code names cannot contain whitespace ({@code ' '}, {@code '\t'},
 * {@code '\n'}, {@code '\f'} or {@code '\r'}), nor commas {@code ','}, nor dots
 * {@code '.'}.</li>
 * <li>Tax codes should be immutable, as far as their {@code name}s,
 * {@code rate}s, and {@code startingOn} dates are concerned. The only “mutable”
 * property should by their {@code stoppingOn} date.</li>
 * </ol>
 *
 * @author Benjamin Gandon
 */
public class SimpleTaxConfig {

    private static final char PROP_NAME_SEGMENT_SEPARATOR = '.';

    /**
     * The prefix to use for configuration properties (either system properties,
     * or per-tenant plugin configuration properties).
     */
    public static final String PROPERTY_PREFIX = "org.killbill.billing.plugin.simpletax.";

    private static final String TAXATION_TIME_ZONE_PROPERTY = PROPERTY_PREFIX + "taxationTimeZone";
    private static final String TAX_AMOUNT_PRECISION_PROPERTY = PROPERTY_PREFIX + "taxItem.amount.precision";
    private static final String TAX_RESOLVER_PROPERTY = PROPERTY_PREFIX + "taxResolver";

    private static final String PRODUCT_TAX_CODE_PREFIX = PROPERTY_PREFIX + "products.";
    private static final String TAX_CODES_PREFIX = PROPERTY_PREFIX + "taxCodes.";

    private static final String TAX_ITEM_DESCRIPTION_SUFFIX = ".taxItem.description";
    private static final String RATE_SUFFIX = ".rate";
    private static final String STARTING_ON_SUFFIX = ".startingOn";
    private static final String STOPPING_ON_SUFFIX = ".stoppingOn";

    /**
     * The default description for
     * {@linkplain org.killbill.billing.invoice.api.InvoiceItemType#TAX TAX
     * items}.
     */
    public static final String DEFAULT_TAX_ITEM_DESC = "tax";
    private static final DateTimeZone DEFAULT_TAXATION_TIME_ZONE = null;
    private static final int DEFAULT_TAX_AMOUNT_PRECISION = 2;
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.00");
    private static final Class<? extends TaxResolver> DEFAULT_RESOLVER = NullTaxResolver.class;
    private static final Constructor<? extends TaxResolver> DEFAULT_RESOLVER_CONSTRUCTOR;
    static {
        try {
            DEFAULT_RESOLVER_CONSTRUCTOR = DEFAULT_RESOLVER.getConstructor(TaxComputationContext.class);
        } catch (NoSuchMethodException shouldNeverHappen) {
            throw new RuntimeException(shouldNeverHappen);
        }
    }

    private Map<String, String> cfg;
    private OSGIKillbillLogService logService;

    private Map<String, TaxCode> taxCodesByName;

    private DateTimeZone taxationTimeZone;
    private int taxAmountPrecision;
    private Constructor<? extends TaxResolver> taxResolverConstructor;

    /**
     * Construct a new configuration accessor for the given configuration
     * properties.
     *
     * @param cfg
     *            The configuration properties to use. No {@code null} values
     *            are allowed, which should be the case when this Map is
     *            constructed out of a {@link java.util.Properties} instance.
     * @param logService
     *            The logging service to use.
     * @throws NullPointerException
     *             when any value in {@code cfg} is {@code null}.
     */
    public SimpleTaxConfig(Map<String, String> cfg, OSGIKillbillLogService logService) {
        this.cfg = cfg;
        this.logService = logService;

        parseConfig();
        earlyConsistencyChecks();
    }

    private void parseConfig() {
        taxationTimeZone = timeZone(cfg, TAXATION_TIME_ZONE_PROPERTY, DEFAULT_TAXATION_TIME_ZONE);
        taxAmountPrecision = integer(cfg, TAX_AMOUNT_PRECISION_PROPERTY, DEFAULT_TAX_AMOUNT_PRECISION);
        taxResolverConstructor = resolverConstructor(cfg, TAX_RESOLVER_PROPERTY, DEFAULT_RESOLVER_CONSTRUCTOR);

        taxCodesByName = parseTaxCodes(cfg);
    }

    private static final String DEFAULT_TAXATION_MSG = " Default taxation of [" + DEFAULT_RESOLVER
            + "] will be applied, which disables any new taxation.";

    /**
     * A bunch of consistency checks, in order for plugin users to acknowledge
     * any configuration issues as early as possible.
     */
    private void earlyConsistencyChecks() {
        String resolverClassName = cfg.get(TAX_RESOLVER_PROPERTY);
        if (isBlank(resolverClassName)) {
            logService.log(LOG_WARNING, "Blank property [" + TAX_RESOLVER_PROPERTY
                    + "], whereas it should not be blank." + DEFAULT_TAXATION_MSG);
        } else {
            Class<?> clazz = null;
            try {
                clazz = currentThread().getContextClassLoader().loadClass(resolverClassName);
            } catch (ClassNotFoundException exc) {
                logService.log(LOG_ERROR, "Cannot load class [" + resolverClassName + "] as specified by the ["
                        + TAX_RESOLVER_PROPERTY + "] configuration property." + DEFAULT_TAXATION_MSG);
            }
            if (clazz != null) {
                String invalidClassMsgPfx = "Invalid class [" + resolverClassName + "] specified by the ["
                        + TAX_RESOLVER_PROPERTY + "] configuration property,";
                if (!TaxResolver.class.isAssignableFrom(clazz)) {
                    logService.log(LOG_ERROR, invalidClassMsgPfx + " which must designate a sub-class of ["
                            + TaxResolver.class.getName() + "]." + DEFAULT_TAXATION_MSG);
                }
                try {
                    clazz.getConstructor(TaxComputationContext.class);
                } catch (NoSuchMethodException exc) {
                    logService.log(LOG_ERROR, invalidClassMsgPfx
                            + " which must define a public constructor accepting a single argument of ["
                            + TaxComputationContext.class.getName() + "]." + DEFAULT_TAXATION_MSG);
                }
            }
        }

        for (Entry<String, String> prop : cfg.entrySet()) {
            String propName = prop.getKey();
            if (!startsWith(propName, PRODUCT_TAX_CODE_PREFIX)) {
                continue;
            }
            String taxCodes = prop.getValue();
            // Here 'taxCodes' is not supposed to be null because that's a
            // requirement we detail in the constructor javadoc.
            for (String taxCode : splitTaxCodes(taxCodes)) {
                if (findTaxCode(taxCode) == null) {
                    logService.log(LOG_ERROR, "Inconsistent config property [" + propName + "] with value [" + taxCodes
                            + "], because the tax code [" + taxCode + "] is not defined anywhere."
                            + " Config spelling error? You should fix this!");
                }
            }
        }
    }

    /**
     * @return A new immutable map of configured tax codes, identified by their
     *         unique names. Never {@code null}, with no {@code null} elements.
     */
    private static Map<String, TaxCode> parseTaxCodes(Map<String, String> cfg) {
        Set<String> names = newLinkedHashSet();
        String lastPropNamePfx = null;
        for (String propName : cfg.keySet()) {
            if (!startsWith(propName, TAX_CODES_PREFIX)) {
                continue;
            }
            if (startsWith(propName, lastPropNamePfx)) {
                continue;
            }
            String taxCodeName = extractName(propName, TAX_CODES_PREFIX.length());
            names.add(taxCodeName);
            lastPropNamePfx = propName.substring(0, TAX_CODES_PREFIX.length() + taxCodeName.length())
                    + PROP_NAME_SEGMENT_SEPARATOR;
        }
        ImmutableMap.Builder<String, TaxCode> codes = ImmutableMap.builder();
        for (String name : names) {
            String prefix = TAX_CODES_PREFIX + name;
            String taxItemDescription = string(cfg, prefix + TAX_ITEM_DESCRIPTION_SUFFIX, DEFAULT_TAX_ITEM_DESC);
            BigDecimal rate = bigDecimal(cfg, prefix + RATE_SUFFIX, DEFAULT_TAX_RATE);
            LocalDate startingOn = localDate(cfg, prefix + STARTING_ON_SUFFIX, null);
            LocalDate stoppingOn = localDate(cfg, prefix + STOPPING_ON_SUFFIX, null);
            codes.put(name, new TaxCode(name, taxItemDescription, rate, startingOn, stoppingOn));
        }
        return codes.build();
    }

    /**
     * Utility method to extract a portion of a property named, supposed to be
     * delimited by {@linkplain #PROP_NAME_SEGMENT_SEPARATOR dots}.
     */
    private static String extractName(String propName, int prefixLength) {
        int endOfNameSegment = indexOf(propName, PROP_NAME_SEGMENT_SEPARATOR, prefixLength);
        if (endOfNameSegment == INDEX_NOT_FOUND) {
            endOfNameSegment = propName.length();
        }
        return propName.substring(TAX_CODES_PREFIX.length(), endOfNameSegment);
    }

    /**
     * @return The time zone to use when considering instants.
     */
    @Nullable
    public DateTimeZone getTaxationTimeZone() {
        return taxationTimeZone;
    }

    /**
     * @return The {@linkplain BigDecimal#setScale scale} to use for amounts in
     *         invoices.
     */
    @Nonnull
    public int getTaxAmountPrecision() {
        return taxAmountPrecision;
    }

    /**
     * A factory for building the configured {@link TaxResolver} implementation.
     *
     * @return The <em>public</em> constructor to use when building the
     *         applicable {@linkplain TaxResolver tax resolver}. Never
     *         {@code null}.
     */
    @Nonnull
    public Constructor<? extends TaxResolver> getTaxResolverConstructor() {
        return taxResolverConstructor;
    }

    /**
     * Finds the definition of a tax code, as identified by its (unique) name.
     *
     * @param name
     *            A name for a tax code.
     * @return A matching tax code from the configuration, or {@code null} if
     *         none matches.
     */
    @Nullable
    public TaxCode findTaxCode(@Nullable String name) {
        return taxCodesByName.get(name);
    }

    /**
     * Lists the configured tax codes for a given product of the catalog.
     *
     * @param productName
     *            The name of a product in the catalog. Should not be
     *            {@code null}.
     * @return A new immutable set of configured tax code definitions. Never
     *         {@code null}, with no {@code null} elements.
     */
    @Nonnull
    public Set<TaxCode> getConfiguredTaxCodes(@Nonnull String productName) {
        String names = cfg.get(PRODUCT_TAX_CODE_PREFIX + productName);
        if (names == null) {
            return ImmutableSet.of();
        }
        return findTaxCodesInternal(names, "taxCode [%1$s] configured for product [" + productName
                + "] is undefined. Config spelling error? Ignoring it.");
    }

    /**
     * Converts a comma-separated list of tax codes into a set of tax code
     * definitions.
     * <p>
     * Tax codes are identified by their name, which are supposed to be unique.
     * The resulting set is ordered in the same way as tax codes were listed,
     * with no duplicates.
     *
     * @param names
     *            A comma-separated list of tax codes names. Must not be
     *            {@code null}.
     * @param errMsgContext
     *            An context message for errors, that tells where the
     *            {@code names} value comes from. Should not be {@code null}.
     * @return A new immutable set of configured tax code definitions that match
     *         the given list of names. Never {@code null}, with no {@code null}
     *         elements.
     * @throws NullPointerException
     *             when {@code names} is null.
     */
    @Nonnull
    public Set<TaxCode> findTaxCodes(@Nonnull String names, @Nonnull String errMsgContext) {
        return findTaxCodesInternal(names, "taxCode [%1$s] " + errMsgContext + " is undefined."
                + " Erroneously removed from config? Ignoring it.");
    }

    /**
     * @param names
     *            A comma-separated list of tax code names. Must not be
     *            {@code null}.
     * @param errMsgFmt
     *            An error message {@linkplain java.util.Formatter format} where
     *            {@code %1$s} will be replaced by the tax code that cannot be
     *            found in the config. Must not be {@code null}.
     * @return A set of configured tax code definitions. Never {@code null}.
     * @throws NullPointerException
     *             when {@code names} is {@code null} or when {@code errMsgFmt}
     *             is {@code null}.
     */
    @Nonnull
    private Set<TaxCode> findTaxCodesInternal(@Nonnull String names, @Nonnull String errMsgFmt) {
        ImmutableSet.Builder<TaxCode> taxCodes = ImmutableSet.builder();
        for (String name : splitTaxCodes(names)) {
            TaxCode taxCode = findTaxCode(name);
            if (taxCode == null) {
                logService.log(LOG_ERROR, String.format(errMsgFmt, name));
                continue;
            }
            taxCodes.add(taxCode);
        }
        return taxCodes.build();
    }
}
