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