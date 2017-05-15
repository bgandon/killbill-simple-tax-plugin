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
import org.killbill.billing.plugin.simpletax.config.http.TaxZoneController.TaxZoneRsc;
import org.killbill.billing.plugin.simpletax.internal.TaxZone;
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
public class TestTaxZoneController {
    private static final TaxZone US = new TaxZone("US");
    private static final TaxZone FR = new TaxZone("FR");

    @Mock
    private OSGIKillbillLogService logService;
    @Mock
    private CustomFieldService customFieldService;

    @InjectMocks
    private TaxZoneController controller;

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
    public void shouldListNoTaxZones() throws Exception {
        // When
        Object resources = controller.listTaxZones(randomUUID(), tenant);

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
    public void shouldListTaxZones() throws Exception {
        // Given
        UUID accountId = randomUUID();
        CustomFieldBuilder builder = new CustomFieldBuilder().withObjectId(accountId);
        when(customFieldService.findAllAccountFieldsByFieldNameAndTenant("taxZone", tenantContext))//
                .thenReturn(newArrayList(//
                        builder.withFieldName("taxZone").withFieldValue("FR").build(),//
                        builder.withFieldName("taxZone").withFieldValue("US").build()));

        // When
        Object resources = controller.listTaxZones(null, tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);

        List<TaxZoneRsc> taxZones = check((List<?>) resources, TaxZoneRsc.class);
        assertEquals(taxZones.size(), 2);

        TaxZoneRsc taxZone1 = taxZones.get(0);
        assertEquals(taxZone1.accountId, accountId);
        assertEquals(taxZone1.taxZone, FR);

        TaxZoneRsc taxZone2 = taxZones.get(1);
        assertEquals(taxZone2.accountId, accountId);
        assertEquals(taxZone2.taxZone, US);
    }

    @Test(groups = "fast")
    public void shouldListNoTaxZone() {
        // Given
        when(customFieldService.findFieldByNameAndAccountAndTenant(anyString(), any(UUID.class), eq(tenantContext)))//
                .thenReturn(null);
        // When
        Object resources = controller.listTaxZones(randomUUID(), tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);
        assertEquals(((List<?>) resources).size(), 0);
    }

    @Test(groups = "fast")
    public void shouldListTaxZoneOfAccount() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("taxZone", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("taxZone")//
                        .withFieldValue("FR")//
                        .build());

        // When
        Object resources = controller.listTaxZones(accountId, tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);
        List<TaxZoneRsc> taxZones = check((List<?>) resources, TaxZoneRsc.class);
        assertEquals(taxZones.size(), 1);

        TaxZoneRsc taxZone1 = taxZones.get(0);
        assertEquals(taxZone1.accountId, accountId);
        assertEquals(taxZone1.taxZone, FR);
    }

    @Test(groups = "fast")
    public void shouldNotListInvalidTaxZoneOfAccount() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("taxZone", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("taxZone")//
                        .withFieldValue("boom!")//
                        .build());

        // When
        Object resources = controller.listTaxZones(accountId, tenant);

        // Then
        assertNotNull(resources);
        assertTrue(resources instanceof List);
        List<TaxZoneRsc> taxZones = check((List<?>) resources, TaxZoneRsc.class);
        assertEquals(taxZones.size(), 0);
    }

    @Test(groups = "fast")
    public void shouldGetTaxZone() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("taxZone", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("taxZone")//
                        .withFieldValue("FR")//
                        .build());

        // When
        Object resource = controller.getAccountTaxZone(accountId, tenant);

        // Then
        assertNotNull(resource);
        assertTrue(resource instanceof TaxZoneRsc);
        TaxZoneRsc taxZone = (TaxZoneRsc) resource;
        assertEquals(taxZone.accountId, accountId);
        assertEquals(taxZone.taxZone, FR);
    }

    @Test(groups = "fast")
    public void shouldGetNoTaxZone() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("taxZone", accountId, tenantContext))//
                .thenReturn(null);

        // Expect
        assertNull(controller.getAccountTaxZone(accountId, tenant));
    }

    @Test(groups = "fast")
    public void shouldGetNoTaxZoneWhenInvalid() {
        // Given
        UUID accountId = randomUUID();
        when(customFieldService.findFieldByNameAndAccountAndTenant("taxZone", accountId, tenantContext))//
                .thenReturn(new CustomFieldBuilder()//
                        .withObjectId(accountId)//
                        .withFieldName("taxZone")//
                        .withFieldValue("boom!")//
                        .build());

        // Expect
        assertNull(controller.getAccountTaxZone(accountId, tenant));
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotAcceptNullResourceWhenSavingTaxZone() {
        // Expect exception
        controller.saveAccountTaxZone(randomUUID(), null, tenant);
    }

    @Test(groups = "fast")
    public void shouldSaveTaxZone() {
        // Given
        UUID accountId = randomUUID();
        TaxZoneRsc taxZone = new TaxZoneRsc(accountId, FR);

        // When
        controller.saveAccountTaxZone(accountId, taxZone, tenant);

        // Then
        verify(customFieldService).saveAccountField("FR", "taxZone", accountId, tenantContext);
    }
}
