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

import static com.google.common.collect.Lists.newArrayList;
import static org.killbill.billing.plugin.TestUtils.buildInvoice;

import java.util.List;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;

public class InvoiceBuilder implements Builder<Invoice> {
    Account account;
    List<InvoiceItemBuilder> itemsBuilders = newArrayList();

    @Override
    public Invoice build() {
        Invoice invoice = buildInvoice(account);
        for (InvoiceItemBuilder itemBuilder : itemsBuilders) {
            itemBuilder.withInvoice(invoice);
            invoice.getInvoiceItems().add(itemBuilder.build());
        }
        return invoice;
    }

    public InvoiceBuilder withAccount(final Account account) {
        this.account = account;
        return this;
    }

    public InvoiceBuilder withItem(final InvoiceItemBuilder itemBuilder) {
        itemsBuilders.add(itemBuilder);
        return this;
    }
}