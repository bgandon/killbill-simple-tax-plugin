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
package org.killbill.billing.plugin.simpletax.config.http;

import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;

public class InvoiceService {
    private static final long START_OFFSET = 0L;
    private static final long PAGE_SIZE = 1L;

    private InvoiceUserApi invoiceApi;
    private static final Logger logger = LoggerFactory.getLogger(InvoiceService.class);

    public InvoiceService(InvoiceUserApi invoiceApi) {
        super();
        this.invoiceApi = invoiceApi;
    }

    @Nonnull
    public List<InvoiceItem> findAllInvoiceItemsByInvoice(UUID invoiceId, TenantContext tenantContext) {
        Invoice invoice;
        try {
            invoice = invoiceApi.getInvoice(invoiceId, tenantContext);
        } catch (InvoiceApiException exc) {
            logger.error("while accessing invoice [" + invoiceId + "] in order to list its items", exc);
            return newArrayList();
        }
        return invoice.getInvoiceItems();
    }

    public Invoice findInvoiceByInvoiceItem(UUID invoiceItemId, TenantContext tenantContext) {
        String searchTerm = invoiceItemId.toString();
        // TODO: ask the Kill Bill guys whether such search query would actually
        // find the right invoice
        Pagination<Invoice> invoices = invoiceApi.searchInvoices(searchTerm, START_OFFSET, PAGE_SIZE, tenantContext);
        Iterator<Invoice> itr = invoices.iterator();
        if (!itr.hasNext()) {
            return null;
        }
        return itr.next();
    }
}
