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
package org.killbill.billing.plugin.simpletax.internal;

import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.killbill.billing.ErrorCode.CAT_NO_SUCH_PLAN;
import static org.killbill.billing.ObjectType.INVOICE_ITEM;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.splitTaxCodes;
import static org.killbill.billing.test.helpers.InvoiceItemBuilder.item;
import static org.killbill.billing.test.helpers.Promise.holder;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.util.LazyValue;
import org.killbill.billing.test.helpers.CustomFieldBuilder;
import org.killbill.billing.test.helpers.InvoiceBuilder;
import org.killbill.billing.test.helpers.Promise;
import org.killbill.billing.test.helpers.TaxCodeBuilder;
import org.killbill.billing.util.customfield.CustomField;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;

/**
 * Tests for {@link TaxCodeService}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestTaxCodeService {

    private static final ImmutableSetMultimap<UUID, TaxCode> EMPTY_TAX_CODES = ImmutableSetMultimap
            .<UUID, TaxCode> of();
    @Mock
    private Product product;
    @Mock
    private Plan plan;
    @Mock
    private StaticCatalog staticCatalog;

    @Mock
    private LazyValue<StaticCatalog, CatalogApiException> catalog;
    @Mock
    private SimpleTaxConfig cfg;

    private SetMultimap<UUID, CustomField> taxFieldsOfInvoices = ImmutableSetMultimap.of();

    private TaxCodeService taxCodeService;

    @Mock
    private Account account;

    private Promise<InvoiceItem> item0 = holder(), item1 = holder(), item3 = holder();
    private Invoice invoice;

    private TaxCode taxA, taxB, taxC;

    @BeforeClass
    public void init() throws CatalogApiException {
        initMocks(this);

        when(catalog.get()).thenReturn(staticCatalog);
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

        taxCodeService = new TaxCodeService(catalog, cfg, taxFieldsOfInvoices);

        invoice = new InvoiceBuilder(account)//
                .withItem(item().withPlanName(null))//
                .withItem(item().withPlanName("plan0").thenSaveTo(item0))//
                .withItem(item().withPlanName("boom!"))//
                .withItem(item().withPlanName("plan1").thenSaveTo(item1))//
                .withItem(item().withPlanName("with-null-product"))//
                .withItem(item().withPlanName("plan3").thenSaveTo(item3))//
                .build();

        TaxCodeBuilder taxBuilder = new TaxCodeBuilder();
        taxA = taxBuilder.withName("taxA").build();
        taxB = taxBuilder.withName("taxB").build();
        taxC = taxBuilder.withName("taxC").build();

        final Map<String, TaxCode> allTaxCodes = ImmutableMap.<String, TaxCode> builder()//
                .put(taxA.getName(), taxA)//
                .put(taxB.getName(), taxB)//
                .put(taxC.getName(), taxC)//
                .build();
        when(cfg.findTaxCodes(anyString(), anyString())).then(new Answer<Set<TaxCode>>() {
            @Override
            public Set<TaxCode> answer(InvocationOnMock invocation) throws Throwable {
                String names = (String) invocation.getArguments()[0];
                ImmutableSet.Builder<TaxCode> defs = ImmutableSet.builder();
                for (String name : splitTaxCodes(names)) {
                    defs.add(allTaxCodes.get(name));
                }
                return defs.build();
            }
        });
    }

    private static ImmutableSetMultimap.Builder<String, TaxCode> taxCfg() {
        return ImmutableSetMultimap.<String, TaxCode> builder();
    }

    private void withTaxes(ImmutableSetMultimap.Builder<String, TaxCode> taxCodesOfProducts) {
        final SetMultimap<String, TaxCode> configuredTaxCodes = taxCodesOfProducts.build();
        when(cfg.getConfiguredTaxCodes(anyString())).then(new Answer<Set<TaxCode>>() {
            @Override
            public Set<TaxCode> answer(InvocationOnMock invocation) throws Throwable {
                String productName = (String) invocation.getArguments()[0];
                Set<TaxCode> taxCodes = configuredTaxCodes.get(productName);
                return taxCodes == null ? ImmutableSet.<TaxCode> of() : taxCodes;
            }
        });
    }

    @Test(groups = "fast")
    public void shouldBeInstanciable() {
        // Expect
        assertNotNull(new TaxCodeService(null, null, null));
    }

    /* *************** Tests for resolveTaxCodesFromConfig() *************** */

    @Test(groups = "fast")
    public void shouldIgnoreNullPlanNames() {
        // Given
        Invoice invoice = new InvoiceBuilder(account)//
                .withItem(item().withPlanName(null))//
                .build();

        // Expect
        assertEquals(taxCodeService.resolveTaxCodesFromConfig(invoice), EMPTY_TAX_CODES);
    }

    @Test(groups = "fast")
    public void shouldIgnoreCatalogApiException() throws CatalogApiException {
        // Given
        Invoice invoice = new InvoiceBuilder(account)//
                .withItem(item().withPlanName("boom!"))//
                .build();

        // Expect
        assertEquals(taxCodeService.resolveTaxCodesFromConfig(invoice), EMPTY_TAX_CODES);
    }

    @Test(groups = "fast")
    public void shouldIgnoreNullProducts() {
        // Given
        Invoice invoice = new InvoiceBuilder(account)//
                .withItem(item().withPlanName("with-null-product"))//
                .build();

        // Expect
        assertEquals(taxCodeService.resolveTaxCodesFromConfig(invoice), EMPTY_TAX_CODES);
    }

    @Test(groups = "fast")
    public void shouldIgnoreEmptyTaxCodes() {
        // Given
        Invoice invoice = new InvoiceBuilder(account)//
                .withItem(item().withPlanName("plan0-no-tax"))//
                .build();

        // Expect
        assertEquals(taxCodeService.resolveTaxCodesFromConfig(invoice), EMPTY_TAX_CODES);
    }

    @Test(groups = "fast")
    public void shouldResolveProperlyAndIgnoreCornerCases() {
        // Given
        withTaxes(taxCfg()//
                .putAll("plan1-product", asList(taxA))//
                .putAll("plan3-product", asList(taxA, taxB, taxC)));

        // When
        SetMultimap<UUID, TaxCode> taxCodesOfInvoiceItems = taxCodeService.resolveTaxCodesFromConfig(invoice);

        // Then
        assertEquals(taxCodesOfInvoiceItems, ImmutableSetMultimap.<UUID, TaxCode> builder()//
                .putAll(item1.get().getId(), asList(taxA))//
                .putAll(item3.get().getId(), asList(taxA, taxB, taxC))//
                .build());
    }

    /* *************** Tests for findExistingTaxCodes() *************** */

    private static final int MAX_UUID_SEARCH_ITERATIONS = 1000;

    private static UUID uuidOtherThan(UUID... otherUUIDs) {
        Set<UUID> others = ImmutableSet.copyOf(otherUUIDs);
        int count = 0;
        while (count < MAX_UUID_SEARCH_ITERATIONS) {
            UUID candidate = randomUUID();
            if (!others.contains(candidate)) {
                return candidate;
            }
            ++count;
        }
        throw new IllegalStateException("Could not find UUID different from " + others);
    }

    @Test(groups = "fast")
    public void shouldIgnoreTaxCodesOnOtherInvoices() {
        // Given
        SetMultimap<UUID, CustomField> taxFieldsOfInvoices = ImmutableSetMultimap.<UUID, CustomField> builder()//
                .put(uuidOtherThan(invoice.getId()), new CustomFieldBuilder()//
                        .withObjectType(INVOICE_ITEM).withObjectId(randomUUID())//
                        .withFieldName("taxCodes").withFieldValue("taxC")//
                        .build())//
                .build();
        TaxCodeService taxCodeService = new TaxCodeService(catalog, cfg, taxFieldsOfInvoices);

        // Expect
        assertEquals(taxCodeService.findExistingTaxCodes(invoice), EMPTY_TAX_CODES);
    }

    @Test(groups = "fast")
    public void shouldIgnoreNonTaxCodesFields() {
        // Given
        SetMultimap<UUID, CustomField> taxFieldsOfInvoices = ImmutableSetMultimap.<UUID, CustomField> builder()//
                .put(invoice.getId(), new CustomFieldBuilder()//
                        .withObjectType(INVOICE_ITEM).withObjectId(item3.get().getId())//
                        .withFieldName("any-other-field-name").withFieldValue("taxC")//
                        .build())//
                .build();
        TaxCodeService taxCodeService = new TaxCodeService(catalog, cfg, taxFieldsOfInvoices);

        // Expect
        assertEquals(taxCodeService.findExistingTaxCodes(invoice), EMPTY_TAX_CODES);
    }

    @Test(groups = "fast")
    public void shouldFindTaxesGrouppedByItems() {
        // Given
        SetMultimap<UUID, CustomField> taxFieldsOfInvoices = ImmutableSetMultimap.<UUID, CustomField> builder()//
                .put(invoice.getId(), new CustomFieldBuilder()//
                        .withObjectType(INVOICE_ITEM).withObjectId(item1.get().getId())//
                        .withFieldName("taxCodes").withFieldValue("taxB")//
                        .build())//
                .put(invoice.getId(), new CustomFieldBuilder()//
                        .withObjectType(INVOICE_ITEM).withObjectId(item3.get().getId())//
                        .withFieldName("taxCodes").withFieldValue("taxC, taxB")//
                        .build())//

                // Tax codes on other item, with null value
                .put(invoice.getId(), new CustomFieldBuilder()//
                        .withObjectType(INVOICE_ITEM).withObjectId(item1.get().getId())//
                        .withFieldName("taxCodes").withFieldValue(null)//
                        .build())//
                // Tax codes on other field name
                .put(invoice.getId(), new CustomFieldBuilder()//
                        .withObjectType(INVOICE_ITEM).withObjectId(item3.get().getId())//
                        .withFieldName("any-other-field-name").withFieldValue("taxC")//
                        .build())//
                // Tax codes on other invoice
                .put(uuidOtherThan(invoice.getId()), new CustomFieldBuilder()//
                        .withObjectType(INVOICE_ITEM).withObjectId(randomUUID())//
                        .withFieldName("taxCodes").withFieldValue("taxC")//
                        .build())//
                .build();
        TaxCodeService taxCodeService = new TaxCodeService(catalog, cfg, taxFieldsOfInvoices);

        // When
        SetMultimap<UUID, TaxCode> taxCodesOfInvoiceItems = taxCodeService.findExistingTaxCodes(invoice);

        // Then
        assertEquals(taxCodesOfInvoiceItems, ImmutableSetMultimap.<UUID, TaxCode> builder()//
                .put(item1.get().getId(), taxB)//
                .putAll(item3.get().getId(), taxC, taxB)//
                .build());
    }

    @Test(groups = "fast")
    public void shouldTrustTheGivenTaxFieldsOfInvoices() {
        // Given
        UUID otherItemId = uuidOtherThan(item0.get().getId(), item1.get().getId(), item3.get().getId());
        SetMultimap<UUID, CustomField> taxFieldsOfInvoices = ImmutableSetMultimap.<UUID, CustomField> builder()//
                // Tax codes on other item that does not exists in invoice
                .put(invoice.getId(), new CustomFieldBuilder()//
                        .withObjectType(INVOICE_ITEM).withObjectId(otherItemId)//
                        .withFieldName("taxCodes").withFieldValue("taxC")//
                        .build())//
                .build();
        TaxCodeService taxCodeService = new TaxCodeService(catalog, cfg, taxFieldsOfInvoices);

        // When
        SetMultimap<UUID, TaxCode> taxCodesOfInvoiceItems = taxCodeService.findExistingTaxCodes(invoice);

        // Then
        assertEquals(taxCodesOfInvoiceItems, ImmutableSetMultimap.<UUID, TaxCode> builder()//
                .put(otherItemId, taxC)//
                .build());
    }
}
