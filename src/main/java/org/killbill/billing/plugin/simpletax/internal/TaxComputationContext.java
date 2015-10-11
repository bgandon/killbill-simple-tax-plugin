package org.killbill.billing.plugin.simpletax.internal;

import java.math.BigDecimal;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.SimpleTaxPluginConfig;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;

/**
 * An immutable holder class that is to contain pre-computed data that are
 * useful when creating new tax items and adjusting existing ones.
 *
 * @author Benjamin Gandon
 */
public class TaxComputationContext {

    /** The plugin configuration. */
    private SimpleTaxPluginConfig config;

    /** The account that currently treated invoice relate to. */
    private Account account;

    /** A function that computes adjusted amounts. */
    private Function<InvoiceItem, BigDecimal> toAdjustedAmount;

    /** An ordering that orders {@link InvoiceItem}s by adjusted amount. */
    private Ordering<InvoiceItem> byAdjustedAmount;

    public TaxComputationContext(final SimpleTaxPluginConfig config, final Account account,
            final Function<InvoiceItem, BigDecimal> toAdjustedAmount, final Ordering<InvoiceItem> byAdjustedAmount) {
        super();
        this.config = config;
        this.account = account;
        this.toAdjustedAmount = toAdjustedAmount;
        this.byAdjustedAmount = byAdjustedAmount;
    }

    public SimpleTaxPluginConfig getConfig() {
        return config;
    }

    public Account getAccount() {
        return account;
    }

    public Function<InvoiceItem, BigDecimal> toAdjustedAmount() {
        return toAdjustedAmount;
    }

    public Ordering<InvoiceItem> byAdjustedAmount() {
        return byAdjustedAmount;
    }
}
