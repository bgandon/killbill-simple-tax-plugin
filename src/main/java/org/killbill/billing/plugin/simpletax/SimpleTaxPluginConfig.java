package org.killbill.billing.plugin.simpletax;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.primitives.Ints.tryParse;
import static java.math.RoundingMode.HALF_UP;
import static org.killbill.billing.plugin.simpletax.SimpleTaxActivator.PROPERTY_PREFIX;

import java.math.BigDecimal;
import java.util.Properties;

public class SimpleTaxPluginConfig {

    private static final String PFX = PROPERTY_PREFIX;

    private static final String TAX_ITEM_DESC_PROPERTY = PFX + "tax-item-description";
    private static final String TAX_AMOUNT_PRECISION_PROPERTY = PFX + "tax-amount-precision";
    private static final String TAX_RATE_PRECISION_PROPERTY = PFX + "tax-rate-precision";
    private static final String TAX_RATE_PROPERTY = PFX + "tax-rate";

    private static final String DEFAULT_TAX_ITEM_DESC = "Tax";
    private static final int DEFAULT_TAX_AMOUNT_PRECISION = 2;
    private static final int DEFAULT_TAX_RATE_PRECISION = 2;
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.20").setScale(DEFAULT_TAX_RATE_PRECISION,
            HALF_UP);

    private String taxItemDescription;
    private int taxAmountPrecision;
    private int taxRatePrecision;
    private BigDecimal taxRate;

    public SimpleTaxPluginConfig(final Properties cfg) {
        taxItemDescription = string(cfg, TAX_ITEM_DESC_PROPERTY, DEFAULT_TAX_ITEM_DESC);

        taxAmountPrecision = integer(cfg, TAX_AMOUNT_PRECISION_PROPERTY, DEFAULT_TAX_AMOUNT_PRECISION);

        taxRatePrecision = integer(cfg, TAX_RATE_PRECISION_PROPERTY, DEFAULT_TAX_RATE_PRECISION);
        taxRate = bigDecimal(cfg, TAX_RATE_PROPERTY, DEFAULT_TAX_RATE, getTaxRatePrecision());
    }

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

    public int getTaxAmountPrecision() {
        return taxAmountPrecision;
    }

    public String getTaxItemDescription() {
        return taxItemDescription;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public int getTaxRatePrecision() {
        return taxRatePrecision;
    }
}
