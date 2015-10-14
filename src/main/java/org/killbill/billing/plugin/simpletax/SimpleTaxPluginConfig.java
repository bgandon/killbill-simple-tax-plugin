package org.killbill.billing.plugin.simpletax;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.primitives.Ints.tryParse;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.Properties;

/**
 * A configuration accessor for the simple-tax plugin.
 *
 * @author Benjamin Gandon
 */
public class SimpleTaxPluginConfig {

    /**
     * The prefix to use for configuration properties (either system properties,
     * or per-tenant plugin configuration properties).
     */
    public static final String PROPERTY_PREFIX = "org.killbill.billing.plugin.simpletax.";

    private static final String TAX_ITEM_DESC_PROPERTY = PROPERTY_PREFIX + "tax-item.description";
    private static final String TAX_AMOUNT_PRECISION_PROPERTY = PROPERTY_PREFIX + "tax-item.amount.precision";
    private static final String TAX_RATE_PRECISION_PROPERTY = PROPERTY_PREFIX + "tax-rate.precision";
    private static final String TAX_RATE_PROPERTY = PROPERTY_PREFIX + "tax-rate";

    private static final String DEFAULT_TAX_ITEM_DESC = "tax";
    private static final int DEFAULT_TAX_AMOUNT_PRECISION = 2;
    private static final int DEFAULT_TAX_RATE_PRECISION = 2;
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.20").setScale(DEFAULT_TAX_RATE_PRECISION,
            HALF_UP);

    private String taxItemDescription;
    private int taxAmountPrecision;
    private int taxRatePrecision;
    private BigDecimal taxRate;

    /**
     * Construct a new configuration accessor for the given configuration
     * properties.
     *
     * @param cfg
     *            The configuration properties to use.
     */
    public SimpleTaxPluginConfig(final Properties cfg) {
        taxItemDescription = string(cfg, TAX_ITEM_DESC_PROPERTY, DEFAULT_TAX_ITEM_DESC);

        taxAmountPrecision = integer(cfg, TAX_AMOUNT_PRECISION_PROPERTY, DEFAULT_TAX_AMOUNT_PRECISION);

        taxRatePrecision = integer(cfg, TAX_RATE_PRECISION_PROPERTY, DEFAULT_TAX_RATE_PRECISION);
        taxRate = bigDecimal(cfg, TAX_RATE_PROPERTY, DEFAULT_TAX_RATE, getTaxRatePrecision());
    }

    /**
     * Utility method to construct a {@link BigDecimal} instance from a
     * configuration property, or return a default value.
     *
     * @param cfg
     *            The configuration properties.
     * @param propName
     *            The property name.
     * @param defaultValue
     *            The default value.
     * @param applicableScale
     *            The default scale to apply.
     * @return A new {@link BigDecimal} instance reflecting the designated
     *         configuration property, or the default value.
     */
    private static BigDecimal bigDecimal(final Properties cfg, final String propName, final BigDecimal defaultValue,
            final int applicableScale) {
        String strValue = cfg.getProperty(propName);
        BigDecimal convertedValue = null;
        try {
            convertedValue = strValue == null ? null : new BigDecimal(strValue);
        } catch (NumberFormatException ignore) {
        }
        BigDecimal value = firstNonNull(convertedValue, defaultValue);
        return value.setScale(applicableScale, HALF_UP);
    }

    private static int integer(final Properties cfg, final String propName, final int defaultValue) {
        String strValue = cfg.getProperty(propName);
        Integer convertedValue = strValue == null ? null : tryParse(strValue);
        return firstNonNull(convertedValue, defaultValue);
    }

    private static String string(final Properties cfg, final String propName, final String defaultValue) {
        return cfg.getProperty(propName, defaultValue);
    }

    /**
     * @return The {@linkplain BigDecimal#setScale scale} to use for amounts in
     *         invoices.
     */
    public int getTaxAmountPrecision() {
        return taxAmountPrecision;
    }

    /**
     * @return The description for tax items in invoices. E.g. {@code "VAT"}.
     */
    public String getTaxItemDescription() {
        return taxItemDescription;
    }

    /**
     * @return The rate to apply when adding tax items to invoice items.
     */
    public BigDecimal getTaxRate() {
        return taxRate;
    }

    /**
     * @return The {@linkplain BigDecimal#setScale scale} that has been used
     *         when constructing {@linkplain #getTaxRate() the tax rate}.
     */
    protected int getTaxRatePrecision() {
        return taxRatePrecision;
    }
}
