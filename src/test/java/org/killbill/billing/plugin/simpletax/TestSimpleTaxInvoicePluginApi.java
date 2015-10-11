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
import static org.killbill.billing.plugin.simpletax.SimpleTaxActivator.PLUGIN_NAME;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.collections.Lists.newArrayList;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.tenant.api.TenantUserApi;
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
    private static final BigDecimal EIGHT = valueOf(8).setScale(DEFAULT_SCALE);
    private static final BigDecimal SEVEN = valueOf(7).setScale(DEFAULT_SCALE);

    private List<PluginProperty> properties = ImmutableList.<PluginProperty> of();
    private CallContext context = new PluginCallContext("killbill-simple-tax", new DateTime(), randomUUID());

    private SimpleTaxInvoicePluginApi plugin;

    @Mock
    InvoiceUserApi invoiceUserApi;
    @Mock
    TenantUserApi tenantUserApi;

    private Account account;
    private Invoice invoiceA, invoiceB, invoiceC, invoiceD, invoiceE;

    private Promise<InvoiceItem> tax1 = new Promise<InvoiceItem>();
    private Promise<InvoiceItem> taxableB = new Promise<InvoiceItem>(), taxableC = new Promise<InvoiceItem>(),
            taxableD = new Promise<InvoiceItem>(), taxableE = new Promise<InvoiceItem>();

    @BeforeClass(groups = "fast")
    public void init() throws Exception {
        initMocks(this);

        account = buildAccount(EUR, "FR");

        OSGIKillbillAPI kbAPI = buildOSGIKillbillAPI(account,
                buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency()), null);
        when(kbAPI.getInvoiceUserApi()).thenReturn(invoiceUserApi);
        when(kbAPI.getTenantUserApi()).thenReturn(tenantUserApi);

        OSGIKillbillLogService logService = buildLogService();
        SimpleTaxConfigurationHandler cfgHandler = new SimpleTaxConfigurationHandler(PLUGIN_NAME, kbAPI, logService);
        cfgHandler.setDefaultConfigurable(new SimpleTaxPluginConfig(new Properties()));

        OSGIConfigPropertiesService cfgService = mock(OSGIConfigPropertiesService.class);
        Clock clock = new DefaultClock();
        plugin = new SimpleTaxInvoicePluginApi(cfgHandler, kbAPI, cfgService, logService, clock);

        Promise<InvoiceItem> taxableA = new Promise<InvoiceItem>();

        invoiceA = new InvoiceBuilder()//
                .withAccount(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(EXTERNAL_CHARGE).withAmount(TEN).thenSaveTo(taxableA))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(TAX).withLinkedItem(taxableA).withAmount(TWO).thenSaveTo(tax1))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxableA).withAmount(ONE.negate()))//
                .build();

        invoiceB = new InvoiceBuilder()//
                .withAccount(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(RECURRING).withAmount(TEN).thenSaveTo(taxableB))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxableA).withAmount(ONE.negate()))//
                .build();

        invoiceC = new InvoiceBuilder()//
                .withAccount(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(EXTERNAL_CHARGE).withAmount(EIGHT).thenSaveTo(taxableC))//
                .build();

        invoiceD = new InvoiceBuilder()//
                .withAccount(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(EXTERNAL_CHARGE).withAmount(EIGHT).thenSaveTo(taxableD))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(TAX).withLinkedItem(taxableD).withAmount(valueOf(1.6)))//
                .build();

        invoiceE = new InvoiceBuilder()//
                .withAccount(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(RECURRING).withAmount(SEVEN).thenSaveTo(taxableE))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxableE).withAmount(TWO))//
                .build();
    }

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
    }

    @Test(groups = "fast")
    public void shouldAdjustIncorrectOldTaxItemAndTaxNewItem() throws Exception {
        // Given
        Invoice newInvoice = invoiceB;
        when(invoiceUserApi.getInvoicesByAccount(any(UUID.class), any(TenantContext.class)))//
                .thenReturn(newArrayList(invoiceA, newInvoice));

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 2);

        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceItemType(), ITEM_ADJ);
        assertEquals(item1.getLinkedItemId(), tax1.get().getId());
        assertEquals(item1.getAmount(), valueOf(-0.4).setScale(DEFAULT_SCALE));
        assertEquals(item1.getInvoiceId(), invoiceA.getId());
        assertEquals(item1.getStartDate(), invoiceA.getInvoiceDate());

        InvoiceItem item2 = items.get(1);
        assertEquals(item2.getInvoiceItemType(), TAX);
        assertEquals(item2.getLinkedItemId(), taxableB.get().getId());
        assertEquals(item2.getAmount(), TWO);
        assertEquals(item2.getInvoiceId(), invoiceB.getId());
        assertEquals(item2.getStartDate(), invoiceB.getInvoiceDate());
    }

    @Test(groups = "fast")
    public void shouldNotCreateNewTaxItemsInNewInvoiceProperlyTaxed() {
        // Given
        Invoice newInvoice = invoiceD;
        when(invoiceUserApi.getInvoicesByAccount(any(UUID.class), any(TenantContext.class)))//
                .thenReturn(newArrayList(newInvoice));

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldNotCreateNewTaxItemsInHistoricalInvoices() {
        // Given
        Invoice newInvoice = invoiceD;
        when(invoiceUserApi.getInvoicesByAccount(any(UUID.class), any(TenantContext.class)))//
                .thenReturn(newArrayList(invoiceC, newInvoice));

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldNotCreateNewTaxItemsInHistoricalInvoicesWithAdjustments() {
        // Given
        Invoice newInvoice = invoiceD;
        when(invoiceUserApi.getInvoicesByAccount(any(UUID.class), any(TenantContext.class)))//
                .thenReturn(newArrayList(invoiceE, newInvoice));

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldCreateMissingTaxItemInNewlyCreatedInvoice() {
        // Given
        Invoice newInvoice = invoiceC;
        when(invoiceUserApi.getInvoicesByAccount(any(UUID.class), any(TenantContext.class)))//
                .thenReturn(newArrayList(invoiceD, newInvoice));

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 1);

        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceId(), invoiceC.getId());
        assertEquals(item1.getInvoiceItemType(), TAX);
        assertEquals(item1.getLinkedItemId(), taxableC.get().getId());
        assertEquals(item1.getAmount(), valueOf(1.6).setScale(DEFAULT_SCALE));
        assertEquals(item1.getStartDate(), invoiceC.getInvoiceDate());
    }

    @Test(groups = "fast")
    public void shouldCreateMissingTaxItemInNewlyCreatedInvoiceWithAdjustment() {
        // Given
        Invoice newInvoice = invoiceE;
        when(invoiceUserApi.getInvoicesByAccount(any(UUID.class), any(TenantContext.class)))//
                .thenReturn(newArrayList(invoiceD, newInvoice));

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 1);

        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceId(), invoiceE.getId());
        assertEquals(item1.getInvoiceItemType(), TAX);
        assertEquals(item1.getLinkedItemId(), taxableE.get().getId());
        assertEquals(item1.getAmount(), valueOf(1.8).setScale(DEFAULT_SCALE));
        assertEquals(item1.getStartDate(), invoiceE.getInvoiceDate());
    }
}
