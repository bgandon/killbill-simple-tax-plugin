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
import static com.googlecode.catchexception.CatchException.catchException;
import static com.googlecode.catchexception.CatchException.caughtException;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.valueOf;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.killbill.billing.ErrorCode.CAT_NO_SUCH_PLAN;
import static org.killbill.billing.ErrorCode.UNEXPECTED_ERROR;
import static org.killbill.billing.ErrorCode.__UNKNOWN_ERROR_CODE;
import static org.killbill.billing.ObjectType.ACCOUNT;
import static org.killbill.billing.ObjectType.INVOICE;
import static org.killbill.billing.ObjectType.INVOICE_ITEM;
import static org.killbill.billing.catalog.api.Currency.EUR;
import static org.killbill.billing.invoice.api.InvoiceItemType.EXTERNAL_CHARGE;
import static org.killbill.billing.invoice.api.InvoiceItemType.ITEM_ADJ;
import static org.killbill.billing.invoice.api.InvoiceItemType.RECURRING;
import static org.killbill.billing.invoice.api.InvoiceItemType.TAX;
import static org.killbill.billing.notification.plugin.api.ExtBusEventType.INVOICE_CREATION;
import static org.killbill.billing.plugin.TestUtils.buildAccount;
import static org.killbill.billing.plugin.TestUtils.buildOSGIKillbillAPI;
import static org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig.PROPERTY_PREFIX;
import static org.killbill.billing.plugin.simpletax.config.TestSimpleTaxConfig.TAX_RESOLVER_PROP;
import static org.killbill.billing.plugin.simpletax.config.http.CustomFieldService.TAX_ZONE_CUSTOM_FIELD_NAME;
import static org.killbill.billing.plugin.simpletax.internal.TaxCodeService.TAX_CODES_FIELD_NAME;
import static org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxActivator.PLUGIN_NAME;
import static org.killbill.billing.test.helpers.Promise.holder;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.osgi.service.log.LogService.LOG_DEBUG;
import static org.osgi.service.log.LogService.LOG_ERROR;
import static org.osgi.service.log.LogService.LOG_INFO;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.notification.plugin.api.ExtBusEvent;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.config.http.CustomFieldService;
import org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxConfigurationHandler;
import org.killbill.billing.plugin.simpletax.resolving.InvoiceItemEndDateBasedResolver;
import org.killbill.billing.plugin.simpletax.resolving.fixtures.AbstractTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.fixtures.InitFailingTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.fixtures.InvalidConstructorTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.fixtures.PrivateConstructorTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.fixtures.ThrowingTaxResolver;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.test.helpers.CustomFieldBuilder;
import org.killbill.billing.test.helpers.InvoiceBuilder;
import org.killbill.billing.test.helpers.InvoiceItemBuilder;
import org.killbill.billing.test.helpers.Promise;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.killbill.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
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

    private static final String VAT_20_0 = "VAT_20_0%";
    private static final String FR = "FR";
    private static final String US = "US";
    private static final BigDecimal TWO = new BigDecimal("2.00");
    private static final BigDecimal SIX = new BigDecimal("6.00");
    private static final BigDecimal SEVEN = new BigDecimal("7.00");
    private static final BigDecimal EIGHT = new BigDecimal("8.00");

    private List<PluginProperty> properties = ImmutableList.<PluginProperty> of();
    @Mock
    private CallContext context;

    @Mock
    private InvoiceUserApi invoiceUserApi;
    @Mock
    private TenantUserApi tenantUserApi;
    @Mock
    private CustomFieldUserApi customFieldUserApi;
    @Mock
    private CatalogUserApi catalogUserApi;

    @Mock
    private CustomFieldService customFieldService;
    private OSGIKillbillAPI services;
    private AccountUserApi accountUserApi;
    @Mock
    private OSGIKillbillLogService logService; // = TestUtils.buildLogService();
    @Mock
    private OSGIConfigPropertiesService cfgService;
    private Clock clock = new DefaultClock();

    private SimpleTaxPlugin plugin;

    private Account account;
    private Invoice invoiceA, invoiceB, invoiceC, invoiceD, invoiceE, invoiceF, invoiceG, invoiceH;

    private Promise<InvoiceItem> tax1, tax2, tax3;
    private Promise<InvoiceItem> taxableA, taxableB, taxableC, taxableD, taxableE, taxableF, taxableG, taxableH;

    private List<CustomField> taxFields = newArrayList();

    private final LocalDate today = new LocalDate("2015-10-25");
    private final LocalDate lastMonth = today.minusMonths(1);

    @Captor
    private ArgumentCaptor<List<CustomField>> fields;

    @BeforeClass(groups = "fast")
    public void init() throws Exception {
    }

    @BeforeMethod(groups = "fast")
    public void setup() throws Exception {
        initMocks(this);

        account = createAccount(FR);

        services = buildOSGIKillbillAPI(account, mock(Payment.class), null);
        accountUserApi = services.getAccountUserApi();
        when(services.getCustomFieldUserApi()).thenReturn(customFieldUserApi);
        when(services.getInvoiceUserApi()).thenReturn(invoiceUserApi);
        when(services.getTenantUserApi()).thenReturn(tenantUserApi);
        when(services.getCatalogUserApi()).thenReturn(catalogUserApi);

        ImmutableMap.Builder<String, String> cfg = ImmutableMap.builder();
        String pfx = PROPERTY_PREFIX;
        cfg.put(pfx + "taxResolver", InvoiceItemEndDateBasedResolver.class.getName());
        cfg.put(pfx + "taxCodes." + VAT_20_0 + ".taxItem.description", "Test VAT");
        cfg.put(pfx + "taxCodes." + VAT_20_0 + ".rate", "0.20");
        cfg.put(pfx + "taxCodes." + VAT_20_0 + ".taxZone", FR);
        cfg.put(pfx + "products.planA-product", VAT_20_0);

        plugin = pluginForConfig(cfg.build());

        initInvoices(VAT_20_0);
    }

    private Account createAccount(String taxZone) {
        Account account = buildAccount(EUR, "ZZ");
        withTaxZoneOf(taxZone, account);
        return account;
    }

    private void withTaxZoneOf(String taxZone, Account account) {
        UUID accountId = account.getId();
        when(
                customFieldService.findFieldByNameAndAccountAndTenant(eq(TAX_ZONE_CUSTOM_FIELD_NAME), eq(accountId),
                        any(TenantContext.class)))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectType(ACCOUNT)//
                        .withObjectId(accountId)//
                        .withFieldName(TAX_ZONE_CUSTOM_FIELD_NAME)//
                        .withFieldValue(taxZone)//
                        .build());
    }

    private SimpleTaxPlugin pluginForConfig(Map<String, String> cfg) {
        SimpleTaxConfigurationHandler cfgHandler = new SimpleTaxConfigurationHandler(PLUGIN_NAME, services, logService);
        cfgHandler.setDefaultConfigurable(new SimpleTaxConfig(cfg, logService));
        return new SimpleTaxPlugin(cfgHandler, customFieldService, services, cfgService, logService, clock);
    }

    private void initInvoices(String taxCode) {
        tax1 = holder();
        tax2 = holder();
        tax3 = holder();
        taxableA = holder();
        taxableB = holder();
        taxableC = holder();
        taxableD = holder();
        taxableE = holder();
        taxableF = holder();
        taxableG = holder();
        taxableH = holder();

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
                        .withType(EXTERNAL_CHARGE).withPlanName("planA").withAmount(EIGHT)//
                        .withEndDate(today).thenSaveTo(taxableD))//
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

        invoiceF = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(RECURRING).withPlanName("planA").withAmount(SIX)//
                        .withStartDate(lastMonth).withEndDate(today)//
                        .thenSaveTo(taxableF))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxableF).withAmount(ONE.negate()))//
                .build();

        invoiceG = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(EXTERNAL_CHARGE).withPlanName("planA").withAmount(TEN)//
                        .withEndDate(today).thenSaveTo(taxableG))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(TAX).withLinkedItem(taxableG).withAmount(TWO).thenSaveTo(tax2))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxableG).withAmount(SIX))//
                .build();

        invoiceH = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(EXTERNAL_CHARGE).withAmount(TEN).thenSaveTo(taxableH))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(TAX).withLinkedItem(taxableH).withAmount(TWO).thenSaveTo(tax3))//
                .build();
    }

    @Mock
    private Product product;
    @Mock
    private Plan plan;
    @Mock
    private StaticCatalog staticCatalog;

    private void initCatalogStub() throws CatalogApiException {
        when(catalogUserApi.getCurrentCatalog(anyString(), any(TenantContext.class)))//
                .thenReturn(staticCatalog);

        final ArgumentCaptor<String> planName = forClass(String.class);
        when(staticCatalog.findCurrentPlan(planName.capture())).then(new Answer<Plan>() {
            @Override
            public Plan answer(InvocationOnMock invocation) throws Throwable {
                String planName = (String) invocation.getArguments()[0];
                if (startsWith(planName, "boom")) {
                    throw new CatalogApiException(CAT_NO_SUCH_PLAN, planName);
                }
                return plan;
            }
        });
        when(plan.getProduct()).then(new Answer<Product>() {
            @Override
            public Product answer(InvocationOnMock invocation) throws Throwable {
                return !startsWith(planName.getValue(), "plan") ? null : product;
            }
        });
        when(product.getName()).then(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return planName.getValue() + "-product";
            }
        });
    }

    private void withInvoices(Invoice... invoices) throws Exception {
        when(invoiceUserApi.getInvoicesByAccount(account.getId(), context))//
                .thenReturn(asList(invoices));

        for (Invoice invoice : invoices) {
            UUID invoiceId = invoice.getId();
            when(invoiceUserApi.getInvoice(eq(invoiceId), any(TenantContext.class))).thenReturn(invoice);
        }

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
    public void shouldAdjustNegativelyIncorrectNewTaxItem() throws Exception {
        // Given
        Invoice newInvoice = invoiceA;
        withInvoices(newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertTrue(items.size() >= 1);
        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceItemType(), ITEM_ADJ);
        assertEquals(item1.getLinkedItemId(), tax1.get().getId());
        assertEquals(item1.getAmount(), new BigDecimal("-0.20"));
        assertEquals(item1.getInvoiceId(), invoiceA.getId());
        assertEquals(item1.getStartDate(), invoiceA.getInvoiceDate());

        assertEquals(items.size(), 1);
    }

    @Test(groups = "fast")
    public void shouldAdjustPositivelyIncorrectNewTaxItem() throws Exception {
        // Given
        initCatalogStub();
        Invoice newInvoice = invoiceG;
        withInvoices(newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertTrue(items.size() >= 1);
        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceItemType(), ITEM_ADJ);
        assertEquals(item1.getLinkedItemId(), tax2.get().getId());
        assertEquals(item1.getAmount(), new BigDecimal("1.20"));
        assertEquals(item1.getInvoiceId(), invoiceG.getId());
        assertEquals(item1.getStartDate(), invoiceG.getInvoiceDate());

        assertEquals(items.size(), 1);
    }

    @Test(groups = "fast")
    public void shouldAdjustIncorrectOldTaxItemAndTaxNewItem() throws Exception {
        // Given
        Invoice newInvoice = invoiceB;
        withInvoices(invoiceA, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertTrue(items.size() >= 1);
        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceItemType(), ITEM_ADJ);
        assertEquals(item1.getLinkedItemId(), tax1.get().getId());
        assertEquals(item1.getAmount(), new BigDecimal("-0.40"));
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
    public void shouldNotCreateNewTaxItemsInNewInvoiceProperlyTaxed() throws Exception {
        // Given
        initCatalogStub();
        Invoice newInvoice = invoiceD;
        withInvoices(newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shoulCreateNewTaxItemsInHistoricalInvoices() throws Exception {
        // Given
        initCatalogStub();
        Invoice newInvoice = invoiceD;
        withInvoices(invoiceC, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 1);
        assertEquals(items.get(0).getAmount(), new BigDecimal("1.60"));
    }

    @Test(groups = "fast")
    public void shouldCreateNewTaxItemsInHistoricalInvoicesWithAdjustments() throws Exception {
        // Given
        initCatalogStub();
        Invoice newInvoice = invoiceD;
        withInvoices(invoiceE, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 1);
        assertEquals(items.get(0).getAmount(), new BigDecimal("1.80"));
    }

    @Test(groups = "fast")
    public void shouldCreateMissingTaxItemInNewlyCreatedInvoice() throws Exception {
        // Given
        Invoice newInvoice = invoiceC;
        withInvoices(invoiceD, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 1);

        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceId(), invoiceC.getId());
        assertEquals(item1.getInvoiceItemType(), TAX);
        assertEquals(item1.getLinkedItemId(), taxableC.get().getId());
        assertEquals(item1.getAmount(), new BigDecimal("1.60"));
        assertEquals(item1.getStartDate(), invoiceC.getInvoiceDate());
    }

    @Test(groups = "fast")
    public void shouldCreateMissingTaxItemInNewlyCreatedInvoiceWithAdjustment() throws Exception {
        // Given
        Invoice newInvoice = invoiceE;
        withInvoices(invoiceD, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertTrue(items.size() >= 1);

        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceId(), invoiceE.getId());
        assertEquals(item1.getInvoiceItemType(), TAX);
        assertEquals(item1.getLinkedItemId(), taxableE.get().getId());
        assertEquals(item1.getAmount(), new BigDecimal("1.80"));
        assertEquals(item1.getStartDate(), invoiceE.getInvoiceDate());

        assertEquals(items.size(), 1);
    }

    @Test(groups = "fast")
    public void shouldCreateNoTaxItemFromConfigWhenCatalogIsNotAvailable() throws Exception {
        // Given
        CallContext context = mock(CallContext.class);
        when(catalogUserApi.getCurrentCatalog(anyString(), eq(context)))//
                .thenThrow(new CatalogApiException(__UNKNOWN_ERROR_CODE));
        Invoice newInvoice = invoiceF;
        withInvoices(invoiceD, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldSupportNullListOfCustomFields() throws Exception {
        // Given
        Account accountWithNullCustomFields = createAccount(FR);
        UUID accountId = accountWithNullCustomFields.getId();
        when(accountUserApi.getAccountById(accountId, context)).thenReturn(accountWithNullCustomFields);

        Invoice invoice = new InvoiceBuilder(accountWithNullCustomFields)//
                .withItem(new InvoiceItemBuilder().withType(RECURRING).withAmount(SIX)).build();

        when(invoiceUserApi.getInvoicesByAccount(accountId, context))//
                .thenReturn(asList(invoice));
        when(customFieldUserApi.getCustomFieldsForAccountType(accountId, INVOICE_ITEM, context))//
                .thenReturn(null);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(invoice, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldAllowEmptyListOfCustomFields() throws Exception {
        // Given
        Account accountNoCustomFields = createAccount(FR);
        UUID accountId = accountNoCustomFields.getId();
        when(accountUserApi.getAccountById(accountId, context)).thenReturn(accountNoCustomFields);

        Invoice invoice = new InvoiceBuilder(accountNoCustomFields)//
                .withItem(new InvoiceItemBuilder().withType(RECURRING).withAmount(SIX)).build();

        when(invoiceUserApi.getInvoicesByAccount(accountId, context))//
                .thenReturn(asList(invoice));
        when(customFieldUserApi.getCustomFieldsForAccountType(accountId, INVOICE_ITEM, context))//
                .thenReturn(ImmutableList.<CustomField> of());

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(invoice, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldAllowNonTaxRelatedCustomFields() throws Exception {
        // Given
        Account accountNonTaxFields = createAccount(FR);
        UUID accountId = accountNonTaxFields.getId();
        when(accountUserApi.getAccountById(accountId, context)).thenReturn(accountNonTaxFields);

        Promise<InvoiceItem> item = holder();
        Invoice invoice = new InvoiceBuilder(accountNonTaxFields)//
                .withItem(new InvoiceItemBuilder().withType(RECURRING).withAmount(SIX).thenSaveTo(item)).build();

        when(invoiceUserApi.getInvoicesByAccount(accountId, context))//
                .thenReturn(asList(invoice));
        when(customFieldUserApi.getCustomFieldsForAccountType(accountId, INVOICE_ITEM, context))//
                .thenReturn(asList(new CustomFieldBuilder()//
                        .withObjectType(INVOICE_ITEM).withObjectId(item.get().getId())//
                        .withFieldName("no-tax-field-name").withFieldValue(VAT_20_0).build()));

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(invoice, properties, context);

        // Then
        assertEquals(items.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldSurviveIllegalTaxResolverWithPrivateConstructor() throws Exception {
        // Given
        SimpleTaxPlugin plugin = pluginForConfig(ImmutableMap.<String, String> of(//
                TAX_RESOLVER_PROP, PrivateConstructorTaxResolver.class.getName()));
        withInvoices(invoiceE);

        // Expect no exception
        assertEquals(plugin.getAdditionalInvoiceItems(invoiceE, properties, context).size(), 0);
    }

    @Test(groups = "fast")
    public void shouldSurviveIllegalTaxResolverWithConstructorAcceptingIncorrectArguments() throws Exception {
        // Given
        SimpleTaxPlugin plugin = pluginForConfig(ImmutableMap.<String, String> of(//
                TAX_RESOLVER_PROP, InvalidConstructorTaxResolver.class.getName()));
        withInvoices(invoiceE);

        // Expect no exception
        assertEquals(plugin.getAdditionalInvoiceItems(invoiceE, properties, context).size(), 0);
    }

    @Test(groups = "fast")
    public void shouldComplainForInstantiationExceptionAtTaxResolverInstanciation() throws Exception {
        // Given
        SimpleTaxPlugin plugin = pluginForConfig(ImmutableMap.<String, String> of(//
                TAX_RESOLVER_PROP, AbstractTaxResolver.class.getName()));
        withInvoices(invoiceE);

        // Expect
        assertEquals(plugin.getAdditionalInvoiceItems(invoiceE, properties, context).size(), 0);
        verify(logService).log(eq(LOG_ERROR), argThat(containsStringIgnoringCase("cannot instanciate tax resolver")),
                isA(InstantiationException.class));
    }

    @Test(groups = "fast")
    public void shouldComplainForInvocationTargetExceptionAtTaxResolverInstanciation() throws Exception {
        // Given
        SimpleTaxPlugin plugin = pluginForConfig(ImmutableMap.<String, String> of(//
                TAX_RESOLVER_PROP, ThrowingTaxResolver.class.getName()));
        withInvoices(invoiceE);

        // Expect
        assertEquals(plugin.getAdditionalInvoiceItems(invoiceE, properties, context).size(), 0);
        verify(logService).log(eq(LOG_ERROR), argThat(containsStringIgnoringCase("cannot instanciate tax resolver")),
                isA(InvocationTargetException.class));
    }

    @Test(groups = "fast")
    public void shouldComplainForExceptionInInitializerErrorAtTaxResolverInstanciation() throws Exception {
        // Given
        SimpleTaxPlugin plugin = pluginForConfig(ImmutableMap.<String, String> of(//
                TAX_RESOLVER_PROP, InitFailingTaxResolver.class.getName()));
        withInvoices(invoiceE);

        // Expect
        assertEquals(plugin.getAdditionalInvoiceItems(invoiceE, properties, context).size(), 0);
        verify(logService).log(eq(LOG_ERROR), argThat(containsStringIgnoringCase("cannot instanciate tax resolver")),
                isA(ExceptionInInitializerError.class));
    }

    @Test(groups = "fast")
    public void shouldNotComplainForErrorWhileAddingTaxCodes() throws Exception {
        // Given
        CallContext context = mock(CallContext.class);
        initCatalogStub();
        doThrow(new CustomFieldApiException(UNEXPECTED_ERROR, ""))//
                .when(customFieldUserApi).addCustomFields(anyListOf(CustomField.class), eq(context));
        withInvoices(invoiceD);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(invoiceD, properties, context);

        // Then
        assertEquals(items.size(), 0);
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldComplainForErrorWhilePersistingTaxCodes() throws Exception {
        // Given
        initCatalogStub();
        doThrow(new CustomFieldApiException(UNEXPECTED_ERROR, ""))//
                .when(customFieldUserApi).addCustomFields(anyListOf(CustomField.class), any(CallContext.class));
        withInvoices(invoiceD);

        ExtBusEvent event = mock(ExtBusEvent.class);
        when(event.getEventType()).thenReturn(INVOICE_CREATION);
        when(event.getObjectType()).thenReturn(INVOICE);
        UUID invoiceId = invoiceD.getId();
        when(event.getObjectId()).thenReturn(invoiceId);

        // When
        catchException(plugin).handleKillbillEvent(event);

        // Then
        Exception exc = caughtException();
        assertNotNull(exc);
        assertEquals(exc.getClass(), RuntimeException.class);
        assertNotNull(exc.getCause());
        assertEquals(exc.getCause().getClass(), CustomFieldApiException.class);

        verify(logService).log(eq(LOG_DEBUG), anyString());
        verify(logService).log(eq(LOG_INFO), anyString());

        verify(logService).log(eq(LOG_ERROR),
                argThat(allOf(containsStringIgnoringCase("cannot add custom field"), containsString(VAT_20_0))),
                any(CustomFieldApiException.class));
        verifyNoMoreInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldCreateMissingTaxItemFromConfiguredTaxCodesForProduct() throws Exception {
        // Given
        CallContext context = mock(CallContext.class);
        initCatalogStub();
        Invoice newInvoice = invoiceF;
        withInvoices(invoiceD, newInvoice);

        ExtBusEvent event = mock(ExtBusEvent.class);
        when(event.getEventType()).thenReturn(INVOICE_CREATION);
        when(event.getObjectType()).thenReturn(INVOICE);
        UUID invoiceId = newInvoice.getId();
        when(event.getObjectId()).thenReturn(invoiceId);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);
        plugin.handleKillbillEvent(event);

        // Then
        assertTrue(items.size() >= 1);

        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceId(), invoiceF.getId());
        assertEquals(item1.getInvoiceItemType(), TAX);
        assertEquals(item1.getLinkedItemId(), taxableF.get().getId());
        assertEquals(item1.getAmount(), new BigDecimal("1.00"));
        assertEquals(item1.getStartDate(), invoiceF.getInvoiceDate());

        assertEquals(items.size(), 1);

        verify(customFieldUserApi).addCustomFields(fields.capture(), any(CallContext.class));
        assertNotNull(fields.getValue());
        assertEquals(fields.getValue().size(), 1);

        CustomField customField = fields.getValue().get(0);
        assertEquals(customField.getObjectType(), INVOICE_ITEM);
        assertEquals(customField.getObjectId(), taxableF.get().getId());
        assertEquals(customField.getFieldName(), "taxCodes");
        assertEquals(customField.getFieldValue(), VAT_20_0);
    }

    @Test(groups = "fast")
    public void shouldFilterOutTaxCodesOnIrrelevantTaxZones() throws Exception {
        // Given
        CallContext context = mock(CallContext.class);
        initCatalogStub();

        Account account = createAccount(US);
        when(accountUserApi.getAccountById(eq(account.getId()), eq(context))).thenReturn(account);

        Promise<InvoiceItem> taxable = holder();
        Invoice newInvoice = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(RECURRING).withPlanName("planA").withAmount(SIX)//
                        .withStartDate(lastMonth).withEndDate(today)//
                        .thenSaveTo(taxable))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxable).withAmount(ONE.negate()))//
                .build();
        withInvoices(newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 0);

        verify(customFieldUserApi, never()).addCustomFields(anyListOf(CustomField.class), eq(context));
    }

    @Test(groups = "fast")
    public void shouldSurviveNoCustomFieldForTaxZone() throws Exception {
        // Given
        CallContext context = mock(CallContext.class);
        initCatalogStub();

        Account account = createAccount("ZZ-boom!");
        UUID accountId = account.getId();
        when(
                customFieldService.findFieldByNameAndAccountAndTenant(eq(TAX_ZONE_CUSTOM_FIELD_NAME), eq(accountId),
                        any(TenantContext.class)))//
                .thenReturn(null);
        when(accountUserApi.getAccountById(eq(accountId), eq(context))).thenReturn(account);

        Promise<InvoiceItem> taxable = holder();
        Invoice newInvoice = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(RECURRING).withPlanName("planA").withAmount(SIX)//
                        .withStartDate(lastMonth).withEndDate(today)//
                        .thenSaveTo(taxable))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxable).withAmount(ONE.negate()))//
                .build();
        withInvoices(newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 0);

        verify(customFieldUserApi, never()).addCustomFields(anyListOf(CustomField.class), eq(context));
        verifyZeroInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldComplainOnInvalidTaxZone() throws Exception {
        // Given
        CallContext context = mock(CallContext.class);
        initCatalogStub();

        Account account = createAccount("ZZ-boom!");
        when(accountUserApi.getAccountById(eq(account.getId()), eq(context))).thenReturn(account);

        Promise<InvoiceItem> taxable = holder();
        Invoice newInvoice = new InvoiceBuilder(account)//
                .withItem(new InvoiceItemBuilder()//
                        .withType(RECURRING).withPlanName("planA").withAmount(SIX)//
                        .withStartDate(lastMonth).withEndDate(today)//
                        .thenSaveTo(taxable))//
                .withItem(new InvoiceItemBuilder()//
                        .withType(ITEM_ADJ).withLinkedItem(taxable).withAmount(ONE.negate()))//
                .build();
        withInvoices(newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertEquals(items.size(), 0);

        verify(customFieldUserApi, never()).addCustomFields(anyListOf(CustomField.class), eq(context));
        String accountIdString = account.getId().toString();
        verify(logService).log(eq(LOG_ERROR),
                argThat(allOf(containsString("ZZ-boom!"), containsString("taxZone"), containsString(accountIdString))),
                any(IllegalArgumentException.class));
    }

    @Test(groups = "fast")
    public void shouldSupportRemovingTaxCodesOnHistoricalInvoices() throws Exception {
        // Given
        initCatalogStub();
        Invoice newInvoice = invoiceD;
        withInvoices(invoiceH, newInvoice);

        // When
        List<InvoiceItem> items = plugin.getAdditionalInvoiceItems(newInvoice, properties, context);

        // Then
        assertTrue(items.size() >= 1);
        InvoiceItem item1 = items.get(0);
        assertEquals(item1.getInvoiceItemType(), ITEM_ADJ);
        assertEquals(item1.getLinkedItemId(), tax3.get().getId());
        assertEquals(item1.getAmount(), new BigDecimal("-2.00"));
        assertEquals(item1.getInvoiceId(), invoiceH.getId());
        assertEquals(item1.getStartDate(), invoiceH.getInvoiceDate());

        assertEquals(items.size(), 1);
    }
}
