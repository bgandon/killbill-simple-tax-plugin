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
package org.killbill.billing.plugin.simpletax;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.internal.Country;
import org.killbill.billing.plugin.simpletax.internal.TaxCodeService;

import java.math.BigDecimal;
import java.util.Set;

/**
 * An immutable holder class that is to contain pre-computed data that are
 * useful when creating new tax items and adjusting existing ones.
 * <p>
 * These pre-computed data are meant to be immutable. They are in most cases.
 * <p>
 * The reason for this “context” class, is for minimize the number of arguments
 * of {@link org.killbill.billing.plugin.simpletax.SimpleTaxPlugin} methods.
 * Inherently to the design of Kill Bill OSGi module, many helper methods and
 * services are provided by the “api” superclass
 * {@link org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi}.
 * <p>
 * This leads plugin writers to keep much of the code in that “api” class, and
 * pass way too many arguments to various computation methods in that class.
 * <p>
 * As a workaround to this issue, we choose to gather here all the pre-computed
 * immutable data that are needed by our methods in our
 * {@link org.killbill.billing.plugin.simpletax.SimpleTaxPlugin} and only pass
 * such a “context” object as argument.
 *
 * @author Benjamin Gandon
 */
/**
 * @author Benjamin Gandon
 */
public class TaxComputationContext {

    private SimpleTaxConfig config;

    private Account account;
    private Country accountTaxCountry;

    private Set<Invoice> allInvoices;

    private Function<InvoiceItem, BigDecimal> toAdjustedAmount;

    private Ordering<InvoiceItem> byAdjustedAmount;

    private TaxCodeService taxCodeService;

    /**
     * Constructs an immutable holder for pre-comuted data.
     *
     * @param config
     *            The plugin configuration.
     * @param account
     *            The account that the newly created invoice relates to.
     * @param accountTaxCountry
     *            The tax country for the given account.
     * @param allInvoices
     *            The set of all invoices for the given account.
     * @param toAdjustedAmount
     *            A function that computes adjusted amounts for the listed
     *            invoices of the given account.
     * @param byAdjustedAmount
     *            An ordering that orders {@link InvoiceItem}s by adjusted
     *            amount.
     * @param taxCodeService
     *            The tax code service to use.
     */
    public TaxComputationContext(SimpleTaxConfig config, Account account, Country accountTaxCountry,
            Set<Invoice> allInvoices, Function<InvoiceItem, BigDecimal> toAdjustedAmount,
            Ordering<InvoiceItem> byAdjustedAmount, TaxCodeService taxCodeService) {
        super();
        this.config = config;
        this.account = account;
        this.accountTaxCountry = accountTaxCountry;
        this.allInvoices = allInvoices;
        this.toAdjustedAmount = toAdjustedAmount;
        this.byAdjustedAmount = byAdjustedAmount;
        this.taxCodeService = taxCodeService;
    }

    /**
     * @return The plugin configuration.
     */
    public SimpleTaxConfig getConfig() {
        return config;
    }

    /**
     * @return The account that the newly created invoice relates to.
     */
    public Account getAccount() {
        return account;
    }

    /**
     * @return The tax country for the {@linkplain #getAccount() given account}.
     */
    public Country getAccountTaxCountry() {
        return accountTaxCountry;
    }

    /**
     * @return The set of all invoices for the {@linkplain #getAccount() given
     *         account}.
     */
    public Set<Invoice> getAllInvoices() {
        return allInvoices;
    }

    /**
     * @return A function that computes adjusted amounts for the
     *         {@linkplain #getAllInvoices() set of invoices} of the
     *         {@linkplain #getAccount() given account}.
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

    /**
     * @return The applicable resolver for tax codes.
     */
    public TaxCodeService getTaxCodeService() {
        return taxCodeService;
    }
}
