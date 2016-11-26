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
package org.killbill.billing.test.helpers;

import static java.util.UUID.randomUUID;
import static org.killbill.billing.catalog.api.Currency.EUR;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.plugin.api.invoice.PluginInvoiceItem;

@SuppressWarnings("javadoc")
public class InvoiceItemBuilder implements Builder<InvoiceItem> {
    private Invoice invoice;
    private InvoiceItemType type;
    private LocalDate startDate, endDate;
    private BigDecimal amount;
    private String planName;
    private Promise<InvoiceItem> linkedItem;

    private Promise<InvoiceItem> builtItemHolder;

    public static InvoiceItemBuilder item() {
        return new InvoiceItemBuilder();
    }

    @Override
    public InvoiceItem build() {
        UUID id = randomUUID();
        UUID invoiceId = invoice == null ? null : invoice.getId();
        UUID accountId = invoice == null ? null : invoice.getAccountId();
        String description = type == null ? null : "Test " + type.name();
        UUID linkedItemId = linkedItem == null ? null : linkedItem.get().getId();
        PluginInvoiceItem item = new PluginInvoiceItem(id, type, invoiceId, accountId, null, startDate, endDate, amount, EUR,
                description, null, null, planName, null, null, linkedItemId, null, null, null);
        if (builtItemHolder != null) {
            builtItemHolder.resolve(item);
        }
        return item;
    }

    public InvoiceItemBuilder withInvoice(Invoice invoice) {
        this.invoice = invoice;
        return this;
    }

    public InvoiceItemBuilder withType(InvoiceItemType type) {
        this.type = type;
        return this;
    }

    public InvoiceItemBuilder withStartDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public InvoiceItemBuilder withEndDate(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public InvoiceItemBuilder withAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public InvoiceItemBuilder withPlanName(String planName) {
        this.planName = planName;
        return this;
    }

    public InvoiceItemBuilder withLinkedItem(Promise<InvoiceItem> linkedItem) {
        this.linkedItem = linkedItem;
        return this;
    }

    public InvoiceItemBuilder thenSaveTo(Promise<InvoiceItem> itemHolder) {
        builtItemHolder = itemHolder;
        return this;
    }
}
