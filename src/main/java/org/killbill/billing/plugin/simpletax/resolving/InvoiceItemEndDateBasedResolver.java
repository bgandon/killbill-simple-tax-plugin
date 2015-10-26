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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.Iterables.tryFind;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.convertTimeZone;

import java.util.Set;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;

import com.google.common.base.Predicate;

/**
 * A {@link TaxResolver} that applies French rules to determine the taxation
 * date.
 * <p>
 * The basic implementation here focuses on the case of service providers.
 * <p>
 * The applicable date here is the date when the service period <em>ended</em>.
 * <p>
 * Source: <a href="http://bofip.impots.gouv.fr/bofip/283-PGP">TVA - Base
 * d'imposition - Fait générateur et exigibilité - Prestations de services</a>
 * <p>
 * <strong>Implementation Notes:</strong>
 * <ul>
 * <li>If any taxation time zone was configured, then the applicable date is
 * interpreted as the first instant of that day in the account time zone, and
 * the taxation date is the date it was in the taxation time zone at that
 * instant.</li>
 * <li>Otherwise, when no taxation time zone is configured, the applicable date
 * is kept interpreted in the time zone of the account.</li>
 * </ul>
 *
 * @author Benjamin Gandon
 */
public class InvoiceItemEndDateBasedResolver implements TaxResolver {

    private SimpleTaxConfig cfg;
    private Account account;

    /**
     * Constructs a new resolver that considers end dates to select the first
     * applicable tax code.
     *
     * @param ctx
     *            The tax computation context to use.
     */
    public InvoiceItemEndDateBasedResolver(TaxComputationContext ctx) {
        super();
        cfg = ctx.getConfig();
        account = ctx.getAccount();
    }

    @Override
    public TaxCode applicableCodeForItem(Set<TaxCode> taxCodes, InvoiceItem item) {
        DateTimeZone accountTimeZone = account.getTimeZone();
        DateTimeZone taxationTimeZone = cfg.getTaxationTimeZone();

        LocalDate applicableDate = firstNonNull(item.getEndDate(), item.getStartDate());

        final LocalDate taxationDate = taxationTimeZone == null ? applicableDate : convertTimeZone(applicableDate,
                accountTimeZone, taxationTimeZone);

        return tryFind(taxCodes, new Predicate<TaxCode>() {
            @Override
            public boolean apply(TaxCode taxCode) {
                LocalDate startDay = taxCode.getStartingOn();
                if ((startDay != null) && taxationDate.isBefore(startDay)) {
                    return false;
                }
                LocalDate stopDay = taxCode.getStoppingOn();
                if ((stopDay != null) && (taxationDate.isEqual(stopDay) || taxationDate.isAfter(stopDay))) {
                    return false;
                }
                return true;
            }
        }).orNull();
    }

}
