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
import org.killbill.billing.plugin.simpletax.config.http.VatinController.VATINRsc;
import org.killbill.billing.plugin.simpletax.internal.VATIN;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.test.helpers.CustomFieldBuilder;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestVATINController {
    private static final String FR_TEST6_NUM = "FR78666666666";
    private static final String FR_TEST7_NUM = "FR89777777777";
    private static final VATIN FR_TEST6 = new VATIN(FR_TEST6_NUM);
    private static final VATIN FR_TEST7 = new VATIN(FR_TEST7_NUM);

    @Mock
    private OSGIKillbillLogService logService;
    @Mock
    private CustomFieldService customFieldService;

    @InjectMocks
    private VatinController controller;

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
        Object resources = controller.listVatins(randomUUID(), tenant);

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
    public void shouldListVATINs() throws Exception {
        // Given
        UUID accountId = randomUUID();
        CustomFieldBuilder builder = new CustomFieldBuilder().withObjectId(accountId);
        when(customFieldService.findAllAccountFieldsByFieldNameAndTenant(eq("VATIdNum"), eq(tenantContext)))//
                .thenReturn(newArrayList(//
                        builder.withFieldName("VATIdNum").withFieldValue(FR_TEST6_NUM).build(),//
                        builder.withFieldName("VATIdNum").withFieldValue(FR_TEST7_NUM).build()));

        // When
        Object resources = controller.listVatins(null, tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);

        List<VATINRsc> taxCountries = check((List<?>) resources, VATINRsc.class);
        assertEquals(taxCountries.size(), 2);

        VATINRsc vatin1 = taxCountries.get(0);
        assertEquals(vatin1.accountId, accountId);
        assertEquals(vatin1.vatin, FR_TEST6);

        VATINRsc vatin2 = taxCountries.get(1);
        assertEquals(vatin2.accountId, accountId);
        assertEquals(vatin2.vatin, FR_TEST7);
    }

    @Test(groups = "fast")
    public void shouldListNoVATIN() {
        // Given
        when(
                customFieldService.findFieldByNameAndAccountAndTenant(anyString(), any(UUID.class),
                        eq(tenantContext)))//
                .thenReturn(null);
        // When
        Object resources = controller.listVatins(randomUUID(), tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);
        assertEquals(((List<?>) resources).size(), 0);
    }

    @Test(groups = "fast")
    public void shouldListVATINOfAccount() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("VATIdNum", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("VATIdNum")//
                        .withFieldValue(FR_TEST6_NUM)//
                        .build());

        // When
        Object resources = controller.listVatins(accountId, tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);
        List<VATINRsc> taxCountries = check((List<?>) resources, VATINRsc.class);
        assertEquals(taxCountries.size(), 1);

        VATINRsc taxCountry1 = taxCountries.get(0);
        assertEquals(taxCountry1.accountId, accountId);
        assertEquals(taxCountry1.vatin, FR_TEST6);
    }

    @Test(groups = "fast")
    public void shouldNotListInvalidVATINOfAccount() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("VATIdNum", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("VATIdNum")//
                        .withFieldValue("boom!")//
                        .build());

        // When
        Object resources = controller.listVatins(accountId, tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);
        List<VATINRsc> vatins = check((List<?>) resources, VATINRsc.class);
        assertEquals(vatins.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldGetTaxCountry() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("VATIdNum", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("VATIdNum")//
                        .withFieldValue(FR_TEST7_NUM)//
                        .build());

        // When
        Object resource = controller.getAccountVatin(accountId, tenant);

        // Then
        assertNotNull(resource);
        assertTrue(resource instanceof VATINRsc);
        VATINRsc vatin = (VATINRsc) resource;
        assertEquals(vatin.accountId, accountId);
        assertEquals(vatin.vatin, FR_TEST7);
    }

    @Test(groups = "fast")
    public void shouldGetNoTaxCountry() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("VATIdNum", accountId, tenantContext))//
                .thenReturn(null);

        // Expect
        assertNull(controller.getAccountVatin(accountId, tenant));
    }

    @Test(groups = "fast")
    public void shouldGetNoTaxCountryWhenInvalid() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("VATIdNum", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("VATIdNum")//
                        .withFieldValue("boom!")//
                        .build());

        // Expect
        assertNull(controller.getAccountVatin(accountId, tenant));
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotAcceptNullResourceWhenSavingVATIN() {
        // Expect exception
        controller.saveAccountVatin(randomUUID(), null, tenant);
    }

    @Test(groups = "fast")
    public void shouldSaveVATIN() {
        // Given
        UUID accountId = randomUUID();
        VATINRsc vatin = new VATINRsc(accountId, FR_TEST6);

        // When
        controller.saveAccountVatin(accountId, vatin, tenant);

        // Then
        verify(customFieldService).saveAccountField(FR_TEST6_NUM, "VATIdNum", accountId, tenantContext);
    }
}
