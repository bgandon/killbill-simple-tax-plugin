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

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.valueOf;
import static java.util.UUID.randomUUID;
import static org.killbill.billing.catalog.api.Currency.EUR;
import static org.killbill.billing.invoice.api.InvoiceItemType.EXTERNAL_CHARGE;
import static org.killbill.billing.invoice.api.InvoiceItemType.ITEM_ADJ;
import static org.killbill.billing.invoice.api.InvoiceItemType.RECURRING;
import static org.killbill.billing.invoice.api.InvoiceItemType.TAX;
import static org.killbill.billing.plugin.TestUtils.buildAccount;
import static org.killbill.billing.plugin.TestUtils.buildLogService;
import static org.killbill.billing.plugin.TestUtils.buildOSGIKillbillAPI;
import static org.killbill.billing.plugin.TestUtils.buildPayment;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.collections.Lists.newArrayList;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.test.helpers.InvoiceBuilder;
import org.killbill.billing.test.helpers.InvoiceItemBuilder;
import org.killbill.billing.test.helpers.Promise;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.killbill.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;
import org.mockito.Mock;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestSimpleTaxInvoicePluginApi {

    private static final int DEFAULT_SCALE = 2;

    private static final BigDecimal TWO = valueOf(2).setScale(DEFAULT_SCALE);

    private List<PluginProperty> properties = ImmutableList.<PluginProperty> of();
    private CallContext context = new PluginCallContext("killbill-simple-tax", new DateTime(), randomUUID());

    private SimpleTaxInvoicePluginApi simpleTaxInvoicePluginApi;

    @Mock
    InvoiceUserApi invoiceUserApi;

    private Account account;
    private Invoice invoice1;
    private Invoice invoice2;

    private Promise<InvoiceItem> tax1 = new Promise<InvoiceItem>();
    private Promise<InvoiceItem> taxable2 = new Promise<InvoiceItem>();

    @BeforeClass(groups = "fast")
    public void init() {
        initMocks(this);
    }

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        account = buildAccount(EUR, "FR");

        OSGIKillbillAPI killbillAPI = buildOSGIKillbillAPI(account,
                buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency()), null);
        when(killbillAPI.getInvoiceUserApi()).thenReturn(invoiceUserApi);

        OSGIConfigPropertiesService configPropertiesService = mock(OSGIConfigPropertiesService.class);
        OSGIKillbillLogService logService = buildLogService();
        Clock clock = new DefaultClock();
        simpleTaxInvoicePluginApi = new SimpleTaxInvoicePluginApi(killbillAPI, configPropertiesService, logService,
                clock);

        Promise<InvoiceItem> taxable1 = new Promise<InvoiceItem>();

        invoice1 = new InvoiceBuilder()//
                .withAccount(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(EXTERNAL_CHARGE).withAmount(TEN).thenSaveTo(taxable1))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(TAX).withLinkedItem(taxable1).withAmount(TWO).thenSaveTo(tax1))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxable1).withAmount(ONE.negate()))//
                .build();

        invoice2 = new InvoiceBuilder()//
                .withAccount(account)//
                .withItem(new InvoiceItemBuilder().withType(RECURRING).withAmount(TEN).thenSaveTo(taxable2))//
                .withItem(new InvoiceItemBuilder().withType(ITEM_ADJ).withLinkedItem(taxable1).withAmount(ONE.negate()))//
                .build();
    }

    @Test(groups = "fast")
    public void shouldAdjustIncorrectOldTaxItemAndTaxNewItem() throws Exception {
        // Given
        Invoice newInvoice = invoice2;
        when(invoiceUserApi.getInvoicesByAccount(any(UUID.class), any(TenantContext.class)))//
                .thenReturn(newArrayList(invoice1, invoice2));

        // When
        List<InvoiceItem> items = simpleTaxInvoicePluginApi.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 2);

        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceItemType(), ITEM_ADJ);
        assertEquals(item1.getLinkedItemId(), tax1.get().getId());
        assertEquals(item1.getAmount(), valueOf(-0.4).setScale(DEFAULT_SCALE));
        assertEquals(item1.getInvoiceId(), invoice1.getId());
        assertEquals(item1.getStartDate(), invoice1.getInvoiceDate());

        InvoiceItem item2 = items.get(1);
        assertEquals(item2.getInvoiceItemType(), TAX);
        assertEquals(item2.getLinkedItemId(), taxable2.get().getId());
        assertEquals(item2.getAmount(), TWO);
        assertEquals(item2.getInvoiceId(), invoice2.getId());
        assertEquals(item2.getStartDate(), invoice2.getInvoiceDate());
    }
}
