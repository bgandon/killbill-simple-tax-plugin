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

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Lists.newArrayList;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.valueOf;
import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static org.killbill.billing.ObjectType.INVOICE_ITEM;
import static org.killbill.billing.catalog.api.Currency.EUR;
import static org.killbill.billing.invoice.api.InvoiceItemType.EXTERNAL_CHARGE;
import static org.killbill.billing.invoice.api.InvoiceItemType.ITEM_ADJ;
import static org.killbill.billing.invoice.api.InvoiceItemType.RECURRING;
import static org.killbill.billing.invoice.api.InvoiceItemType.TAX;
import static org.killbill.billing.plugin.TestUtils.buildAccount;
import static org.killbill.billing.plugin.TestUtils.buildLogService;
import static org.killbill.billing.plugin.TestUtils.buildOSGIKillbillAPI;
import static org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig.PROPERTY_PREFIX;
import static org.killbill.billing.plugin.simpletax.internal.TaxCodeService.TAX_CODES_FIELD_NAME;
import static org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxActivator.PLUGIN_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxConfigurationHandler;
import org.killbill.billing.plugin.simpletax.resolving.InvoiceItemEndDateBasedResolver;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.test.helpers.CustomFieldBuilder;
import org.killbill.billing.test.helpers.InvoiceBuilder;
import org.killbill.billing.test.helpers.InvoiceItemBuilder;
import org.killbill.billing.test.helpers.Promise;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.killbill.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;
import org.mockito.Mock;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Tests for {@link SimpleTaxPlugin}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestSimpleTaxPlugin {

    private static final int DEFAULT_SCALE = 2;

    private static final BigDecimal TWO = valueOf(2).setScale(DEFAULT_SCALE);
    private static final BigDecimal EIGHT = valueOf(8).setScale(DEFAULT_SCALE);
    private static final BigDecimal SEVEN = valueOf(7).setScale(DEFAULT_SCALE);

    private List<PluginProperty> properties = ImmutableList.<PluginProperty> of();
    private CallContext context = new PluginCallContext("killbill-simple-tax", new DateTime(), randomUUID());

    private SimpleTaxPlugin plugin;

    @Mock
    private InvoiceUserApi invoiceUserApi;
    @Mock
    private TenantUserApi tenantUserApi;
    @Mock
    private CustomFieldUserApi customFieldUserApi;

    private Account account;
    private Invoice invoiceA, invoiceB, invoiceC, invoiceD, invoiceE;

    private Promise<InvoiceItem> tax1 = new Promise<InvoiceItem>();
    private Promise<InvoiceItem> taxableA = new Promise<InvoiceItem>(),//
            taxableB = new Promise<InvoiceItem>(),//
            taxableC = new Promise<InvoiceItem>(),//
            taxableD = new Promise<InvoiceItem>(),//
            taxableE = new Promise<InvoiceItem>();

    private List<CustomField> taxFields = newArrayList();

    @BeforeClass(groups = "fast")
    public void init() throws Exception {
        initMocks(this);

        account = buildAccount(EUR, "FR");

        OSGIKillbillAPI services = buildOSGIKillbillAPI(account, mock(Payment.class), null);
        when(services.getCustomFieldUserApi()).thenReturn(customFieldUserApi);
        when(services.getInvoiceUserApi()).thenReturn(invoiceUserApi);
        when(services.getTenantUserApi()).thenReturn(tenantUserApi);

        OSGIKillbillLogService logService = buildLogService();
        SimpleTaxConfigurationHandler cfgHandler = new SimpleTaxConfigurationHandler(PLUGIN_NAME, services, logService);
        ImmutableMap.Builder<String, String> cfg = ImmutableMap.builder();
        String pfx = PROPERTY_PREFIX;
        cfg.put(pfx + "taxResolver", InvoiceItemEndDateBasedResolver.class.getName());
        String taxCode = "VAT_20_0%";
        cfg.put(pfx + "taxCodes." + taxCode + ".taxItem.description", "Test VAT");
        cfg.put(pfx + "taxCodes." + taxCode + ".rate", "0.20");
        // cfg.put(pfx + "taxCodes."+taxCode+".startingOn", "2015-10-10");
        // cfg.put(pfx + "taxCodes."+taxCode+".stoppingOn", "2015-10-10");
        cfgHandler.setDefaultConfigurable(new SimpleTaxConfig(cfg.build(), logService));

        OSGIConfigPropertiesService cfgService = mock(OSGIConfigPropertiesService.class);
        Clock clock = new DefaultClock();
        plugin = new SimpleTaxPlugin(cfgHandler, services, cfgService, logService, clock);

        // when(customFieldUserApi.getCustomFieldsForAccountType(account.getId(),
        // INVOICE_ITEM, context)).thenReturn(
        // taxFields);
        CustomFieldBuilder twentyPerCentVatFieldBuilder = new CustomFieldBuilder()//
                .withObjectType(INVOICE_ITEM)//
                .withFieldName(TAX_CODES_FIELD_NAME)//
                .withFieldValue(taxCode);

        invoiceA = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(EXTERNAL_CHARGE).withAmount(TEN).thenSaveTo(taxableA))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(TAX).withLinkedItem(taxableA).withAmount(TWO).thenSaveTo(tax1))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxableA).withAmount(ONE.negate()))//
                .build();
        taxFields.add(twentyPerCentVatFieldBuilder.withObjectId(taxableA.get().getId()).build());

        invoiceB = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(RECURRING).withAmount(TEN).thenSaveTo(taxableB))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxableA).withAmount(ONE.negate()))//
                .build();
        taxFields.add(twentyPerCentVatFieldBuilder.withObjectId(taxableB.get().getId()).build());

        invoiceC = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(EXTERNAL_CHARGE).withAmount(EIGHT).thenSaveTo(taxableC))//
                .build();
        taxFields.add(twentyPerCentVatFieldBuilder.withObjectId(taxableC.get().getId()).build());

        invoiceD = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(EXTERNAL_CHARGE).withAmount(EIGHT).thenSaveTo(taxableD))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(TAX).withLinkedItem(taxableD).withAmount(valueOf(1.6)))//
                .build();
        taxFields.add(twentyPerCentVatFieldBuilder.withObjectId(taxableD.get().getId()).build());

        invoiceE = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(RECURRING).withAmount(SEVEN).thenSaveTo(taxableE))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxableE).withAmount(TWO))//
                .build();
        taxFields.add(twentyPerCentVatFieldBuilder.withObjectId(taxableE.get().getId()).build());
    }

    private void withInvoices(Invoice... invoices) {
        when(invoiceUserApi.getInvoicesByAccount(account.getId(), context))//
                .thenReturn(asList(invoices));

        List<CustomField> fields = fieldsRelatedTo(invoices);

        when(customFieldUserApi.getCustomFieldsForAccountType(account.getId(), INVOICE_ITEM, context))//
                .thenReturn(fields);
    }

    private List<CustomField> fieldsRelatedTo(Invoice... invoices) {
        ImmutableSet.Builder<UUID> knownItemIdentifiers = ImmutableSet.builder();
        for (Invoice invoice : invoices) {
            for (InvoiceItem item : invoice.getInvoiceItems()) {
                knownItemIdentifiers.add(item.getId());
            }
        }
        final Set<UUID> knownItems = knownItemIdentifiers.build();
        List<CustomField> fieldsForInvoices = newArrayList(filter(taxFields, new Predicate<CustomField>() {
            @Override
            public boolean apply(CustomField field) {
                return knownItems.contains(field.getObjectId());
            }
        }));
        return fieldsForInvoices;
    }

    @Test(groups = "fast")
    public void shouldAdjustIncorrectOldTaxItemAndTaxNewItem() throws Exception {
        // Given
        Invoice newInvoice = invoiceB;
        withInvoices(invoiceA, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, false, properties, context);

        // Then
        assertTrue(items.size() >= 1);
        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceItemType(), ITEM_ADJ);
        assertEquals(item1.getLinkedItemId(), tax1.get().getId());
        assertEquals(item1.getAmount(), valueOf(-0.4).setScale(DEFAULT_SCALE));
        assertEquals(item1.getInvoiceId(), invoiceA.getId());
        assertEquals(item1.getStartDate(), invoiceA.getInvoiceDate());

        assertTrue(items.size() >= 2);
        InvoiceItem item2 = items.get(1);
        assertEquals(item2.getInvoiceItemType(), TAX);
        assertEquals(item2.getLinkedItemId(), taxableB.get().getId());
        assertEquals(item2.getAmount(), TWO);
        assertEquals(item2.getInvoiceId(), invoiceB.getId());
        assertEquals(item2.getStartDate(), invoiceB.getInvoiceDate());

        assertEquals(items.size(), 2);
    }

    @Test(groups = "fast")
    public void shouldNotCreateNewTaxItemsInNewInvoiceProperlyTaxed() {
        // Given
        Invoice newInvoice = invoiceD;
        withInvoices(newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, false, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldNotCreateNewTaxItemsInHistoricalInvoices() {
        // Given
        Invoice newInvoice = invoiceD;
        withInvoices(invoiceC, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, false, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldNotCreateNewTaxItemsInHistoricalInvoicesWithAdjustments() {
        // Given
        Invoice newInvoice = invoiceD;
        withInvoices(invoiceE, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, false, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldCreateMissingTaxItemInNewlyCreatedInvoice() {
        // Given
        Invoice newInvoice = invoiceC;
        withInvoices(invoiceD, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, false, properties, context);

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
        withInvoices(invoiceD, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, false, properties, context);

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
