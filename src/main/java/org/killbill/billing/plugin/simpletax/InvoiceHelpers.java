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

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.killbill.billing.invoice.api.InvoiceItem;

import com.google.common.collect.Multimap;

/**
 * Helper utility methods that perform computations on invoices.
 *
 * @author Benjamin Gandon
 */
public class InvoiceHelpers {

    /**
     * Utility method that computes the amount of a given
     * {@linkplain InvoiceItem invoice item}, taking any adjustments into
     * consideration.
     *
     * @param item
     *            An invoice item. Must not be {@code null}.
     * @param allAdjustments
     *            All the adjustments items of all invoices (for the same
     *            account as the invoice item above). Must not be {@code null}.
     * @return the adjusted amount, never {@code null}.
     * @throws NullPointerException
     *             when {@code item} or {@code allAdjustments} are {@code null}.
     */
    @Nonnull
    public static BigDecimal amountWithAdjustments(@Nonnull InvoiceItem item,
            @Nonnull Multimap<UUID, InvoiceItem> allAdjustments) {
        Iterable<InvoiceItem> adjustments = allAdjustments.get(item.getId());
        BigDecimal amount = sumItems(adjustments);
        if (item.getAmount() != null) {
            amount = amount.add(item.getAmount());
        }
        return amount;
    }

    /**
     * Utility method that sums the given amounts.
     *
     * @param amounts
     *            the amounts to sum, non of which can be {@code null}.
     * @return the sum of amounts, never {@code null}.
     */
    public static BigDecimal sumAmounts(@Nullable Iterable<BigDecimal> amounts) {
        BigDecimal sum = ZERO;
        for (BigDecimal amount : amounts) {
            sum = sum.add(amount);
        }
        return sum;
    }

    /**
     * Utility method that sums the amounts of the given invoice items.
     * <p>
     * {@linkplain InvoiceItem Items} cannot be {@code null}, but their
     * {@linkplain InvoiceItem#getAmount amount} can. Any {@code null} value is
     * just discarded.
     * <p>
     * The resulting sum does not take any adjustment into consideration.
     *
     * @param invoiceItems
     *            The invoice items to sum, or {@code null}. The collection
     *            shall not contain any {@code null} items, but those can
     *            possibly have {@code null} amounts.
     * @return the sum of all invoice items amount, never {@code null}.
     * @throws NullPointerException
     *             When any {@link InvoiceItem} in {@code invoiceItems} is
     *             {@code null}.
     */
    @Nonnull
    public static BigDecimal sumItems(@Nullable Iterable<InvoiceItem> invoiceItems) {
        if (invoiceItems == null) {
            return ZERO;
        }
        BigDecimal sum = ZERO;
        for (InvoiceItem item : invoiceItems) {
            if (item.getAmount() != null) {
                sum = sum.add(item.getAmount());
            }
        }
        return sum;
    }
}
