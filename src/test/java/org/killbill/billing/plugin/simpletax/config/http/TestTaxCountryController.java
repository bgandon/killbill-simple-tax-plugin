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

import static com.google.common.collect.Lists.newArrayList;
import static java.util.UUID.randomUUID;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.plugin.api.PluginTenantContext;
import org.killbill.billing.plugin.simpletax.config.http.TaxCountryController.TaxCountryRsc;
import org.killbill.billing.plugin.simpletax.internal.Country;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.test.helpers.CustomFieldBuilder;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillLogService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestTaxCountryController {
    private static final Country US = new Country("US");
    private static final Country FR = new Country("FR");

    @Mock
    private OSGIKillbillLogService logService;
    @Mock
    private CustomFieldService customFieldService;

    @InjectMocks
    private TaxCountryController controller;

    @Mock
    private Tenant tenant;
    private TenantContext tenantContext;

    @BeforeMethod
    public void setup() {
        initMocks(this);
        when(tenant.getId()).thenReturn(randomUUID());
        tenantContext = new PluginTenantContext(tenant.getId());
    }

    @Test(groups = "fast")
    public void shouldListNoTaxCountries() throws Exception {
        // When
        Object resources = controller.listTaxCountries(randomUUID(), tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);
        assertEquals(((List<?>) resources).size(), 0);
    }

    private static <T> List<T> check(Iterable<?> iterable, Class<T> type) {
        if (iterable == null) {
            return null;
        }
        List<T> checked = newArrayList();
        int idx = 0;
        for (Object obj : iterable) {
            if (!type.isInstance(obj)) {
                throw new ClassCastException("cannot cast list element #" + idx + " of [" + obj + "] to "
                        + type.getName());
            }
            checked.add(type.cast(obj));
            ++idx;
        }
        return checked;
    }

    @Test(groups = "fast")
    public void shouldListTaxCountries() throws Exception {
        // Given
        UUID accountId = randomUUID();
        CustomFieldBuilder builder = new CustomFieldBuilder().withObjectId(accountId);
        when(customFieldService.findAllAccountFieldsByFieldNameAndTenant("taxCountry", tenantContext))//
                .thenReturn(newArrayList(//
                        builder.withFieldName("taxCountry").withFieldValue("FR").build(),//
                        builder.withFieldName("taxCountry").withFieldValue("US").build()));

        // When
        Object resources = controller.listTaxCountries(null, tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);

        List<TaxCountryRsc> taxCountries = check((List<?>) resources, TaxCountryRsc.class);
        assertEquals(taxCountries.size(), 2);

        TaxCountryRsc taxCountry1 = taxCountries.get(0);
        assertEquals(taxCountry1.accountId, accountId);
        assertEquals(taxCountry1.taxCountry, FR);

        TaxCountryRsc taxCountry2 = taxCountries.get(1);
        assertEquals(taxCountry2.accountId, accountId);
        assertEquals(taxCountry2.taxCountry, US);
    }

    @Test(groups = "fast")
    public void shouldListNoTaxCountry() {
        // Given
        when(
                customFieldService.findFieldByNameAndAccountAndTenant(anyString(), any(UUID.class),
                        eq(tenantContext)))//
                .thenReturn(null);
        // When
        Object resources = controller.listTaxCountries(randomUUID(), tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);
        assertEquals(((List<?>) resources).size(), 0);
    }

    @Test(groups = "fast")
    public void shouldListTaxCountryOfAccount() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("taxCountry", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("taxCountry")//
                        .withFieldValue("FR")//
                        .build());

        // When
        Object resources = controller.listTaxCountries(accountId, tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);
        List<TaxCountryRsc> taxCountries = check((List<?>) resources, TaxCountryRsc.class);
        assertEquals(taxCountries.size(), 1);

        TaxCountryRsc taxCountry1 = taxCountries.get(0);
        assertEquals(taxCountry1.accountId, accountId);
        assertEquals(taxCountry1.taxCountry, FR);
    }

    @Test(groups = "fast")
    public void shouldNotListInvalidTaxCountryOfAccount() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("taxCountry", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("taxCountry")//
                        .withFieldValue("boom!")//
                        .build());

        // When
        Object resources = controller.listTaxCountries(accountId, tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);
        List<TaxCountryRsc> taxCountries = check((List<?>) resources, TaxCountryRsc.class);
        assertEquals(taxCountries.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldGetTaxCountry() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("taxCountry", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("taxCountry")//
                        .withFieldValue("FR")//
                        .build());

        // When
        Object resource = controller.getAccountTaxCountry(accountId, tenant);

        // Then
        assertNotNull(resource);
        assertTrue(resource instanceof TaxCountryRsc);
        TaxCountryRsc taxCountry = (TaxCountryRsc) resource;
        assertEquals(taxCountry.accountId, accountId);
        assertEquals(taxCountry.taxCountry, FR);
    }

    @Test(groups = "fast")
    public void shouldGetNoTaxCountry() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("taxCountry", accountId, tenantContext))//
                .thenReturn(null);

        // Expect
        assertNull(controller.getAccountTaxCountry(accountId, tenant));
    }

    @Test(groups = "fast")
    public void shouldGetNoTaxCountryWhenInvalid() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("taxCountry", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("taxCountry")//
                        .withFieldValue("boom!")//
                        .build());

        // Expect
        assertNull(controller.getAccountTaxCountry(accountId, tenant));
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotAcceptNullResourceWhenSavingTaxCountry() {
        // Expect exception
        controller.saveAccountTaxCountry(randomUUID(), null, tenant);
    }

    @Test(groups = "fast")
    public void shouldSaveTaxCountry() {
        // Given
        UUID accountId = randomUUID();
        TaxCountryRsc taxCountry = new TaxCountryRsc(accountId, FR);

        // When
        controller.saveAccountTaxCountry(accountId, taxCountry, tenant);

        // Then
        verify(customFieldService).saveAccountField("FR", "taxCountry", accountId, tenantContext);
    }
}
