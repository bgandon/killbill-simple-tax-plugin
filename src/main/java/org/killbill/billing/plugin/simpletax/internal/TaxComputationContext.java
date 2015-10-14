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
package org.killbill.billing.plugin.simpletax.internal;

import java.math.BigDecimal;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.SimpleTaxConfig;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;

/**
 * An immutable holder class that is to contain pre-computed data that are
 * useful when creating new tax items and adjusting existing ones.
 *
 * @author Benjamin Gandon
 */
public class TaxComputationContext {

    private SimpleTaxConfig config;

    private Account account;

    private Function<InvoiceItem, BigDecimal> toAdjustedAmount;

    private Ordering<InvoiceItem> byAdjustedAmount;

    /**
     * Constructs an immutable holder for pre-comuted data.
     *
     * @param config
     *            The plugin configuration.
     * @param account
     *            The account that currently treated invoice relate to.
     * @param toAdjustedAmount
     *            A function that computes adjusted amounts.
     * @param byAdjustedAmount
     *            An ordering that orders {@link InvoiceItem}s by adjusted
     *            amount.
     */
    public TaxComputationContext(final SimpleTaxConfig config, final Account account,
            final Function<InvoiceItem, BigDecimal> toAdjustedAmount, final Ordering<InvoiceItem> byAdjustedAmount) {
        super();
        this.config = config;
        this.account = account;
        this.toAdjustedAmount = toAdjustedAmount;
        this.byAdjustedAmount = byAdjustedAmount;
    }

    /**
     * @return The plugin configuration.
     */
    public SimpleTaxConfig getConfig() {
        return config;
    }

    /**
     * @return The account that currently treated invoice relate to.
     */
    public Account getAccount() {
        return account;
    }

    /**
     * @return A function that computes adjusted amounts.
     */
    public Function<InvoiceItem, BigDecimal> toAdjustedAmount() {
        return toAdjustedAmount;
    }

    /**
     * @return An ordering that orders {@link InvoiceItem}s by adjusted amount.
     */
    public Ordering<InvoiceItem> byAdjustedAmount() {
        return byAdjustedAmount;
    }
}
