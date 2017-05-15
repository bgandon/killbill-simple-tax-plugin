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

import static java.util.UUID.randomUUID;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.killbill.billing.plugin.simpletax.config.http.TaxZoneController.TaxZoneRsc;
import org.killbill.billing.plugin.simpletax.config.http.VatinController.VATINRsc;
import org.killbill.billing.plugin.simpletax.internal.TaxZone;
import org.killbill.billing.plugin.simpletax.internal.VATIN;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.test.helpers.ServletMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestSimpleTaxServlet {
    private static final String FR = "FR";
    private static final TaxZone FRANCE = new TaxZone(FR);

    private static final String FR_TEST6_VATIN_NUM = "FR78666666666";
    private static final VATIN FR_TEST6_VATIN = new VATIN(FR_TEST6_VATIN_NUM);

    private static final String VATINS_RSC_URI = "/vatins";
    private static final String TAX_COUNTRIES_RSC_URI = "/taxZones";
    private static final String APPLICATION_JSON = "application/json";
    private static final String ACCOUNT_PARAM_NAME = "account";

    @DataProvider(name = "invalidAccountUUIDs")
    public static Object[][] invalidAccountUUIDss() {
        return new Object[][] { { "plop" }, { "whatever-uuid-_pat-tern-_conforming_" },
                { "12345678-plop-what-ever-123456789abc" } };
    }

    @Mock
    private TaxZoneController taxZoneController;
    @Mock
    private VatinController vatinController;

    @InjectMocks
    private SimpleTaxServlet servlet;

    @Captor
    private ArgumentCaptor<TaxZoneRsc> taxZoneRsc;
    @Captor
    private ArgumentCaptor<VATINRsc> vatinRsc;

    @BeforeMethod
    public void setup() {
        initMocks(this);
    }

    // ==================== GET ====================

    @Test(groups = "fast")
    public void shouldRespondNotFoundWhenDispatchingGetWithNoTenant() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_NOT_FOUND);
    }

    private static Tenant withTenant(HttpServletRequest req) {
        UUID tenantId = randomUUID();
        Tenant tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(tenantId);

        when(req.getAttribute("killbill_tenant")).thenReturn(tenant);
        return tenant;
    }

    // ========== GET /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/... ==========

    @Test(groups = "fast", dataProvider = "invalidAccountUUIDs")
    public void shouldRespondNotFoundWhenDispatchingGetAccountResourceWithNoAccount(String accountId) throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        withTenant(mocks.req());
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/taxZone");

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_NOT_FOUND);
    }

    @Test(groups = "fast")
    public void shouldDispatchGetAccountTaxZone() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/taxZone");

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(vatinController);
        verify(taxZoneController).getAccountTaxZone(accountId, tenant);
        assertEquals(mocks.getResponseContentType(), APPLICATION_JSON);
        assertEquals(mocks.getResponseStatus(), SC_OK);
    }

    @Test(groups = "fast")
    public void shouldRenderAccountTaxZone() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/taxZone");

        TaxZoneRsc rsc = new TaxZoneRsc(accountId, FRANCE);
        when(taxZoneController.getAccountTaxZone(accountId, tenant)).thenReturn(rsc);

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        assertEquals(mocks.getResponseContentType(), APPLICATION_JSON);
        assertEquals(mocks.getResponseStatus(), SC_OK);
        assertEquals(mocks.getResponseContent(), "{\"accountId\":\"" + accountId + "\",\"taxZone\":\"" + FR + "\"}");
    }

    @Test(groups = "fast")
    public void shouldDispatchGetAccountVATIN() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/vatin");

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController);
        verify(vatinController).getAccountVatin(eq(accountId), eq(tenant));
        assertEquals(mocks.getResponseContentType(), APPLICATION_JSON);
        assertEquals(mocks.getResponseStatus(), SC_OK);
    }

    @Test(groups = "fast")
    public void shouldRenderAccountVATIN() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/taxZone");

        VATINRsc rsc = new VATINRsc(accountId, FR_TEST6_VATIN);
        when(taxZoneController.getAccountTaxZone(accountId, tenant)).thenReturn(rsc);

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        assertEquals(mocks.getResponseContentType(), APPLICATION_JSON);
        assertEquals(mocks.getResponseStatus(), SC_OK);
        assertEquals(mocks.getResponseContent(), "{\"accountId\":\"" + accountId + "\",\"vatin\":\""
                + FR_TEST6_VATIN_NUM + "\"}");
    }

    @Test(groups = "fast")
    public void shouldRespondNotFoundWhenDispatchingGetAccountWithUnknownResource() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        withTenant(mocks.req());

        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + randomUUID() + "/plop");

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_NOT_FOUND);
    }

    // ==================== GET /taxZones?account=... ====================

    @Test(groups = "fast")
    public void shouldDispatchGetTaxZones() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        when(mocks.req().getPathInfo()).thenReturn(TAX_COUNTRIES_RSC_URI);

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(vatinController);
        verify(taxZoneController).listTaxZones(null, tenant);
        assertEquals(mocks.getResponseContentType(), APPLICATION_JSON);
        assertEquals(mocks.getResponseStatus(), SC_OK);
    }

    @Test(groups = "fast")
    public void shouldDispatchGetTaxZonesWhenAccountRestrictionIsBlank() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        when(mocks.req().getPathInfo()).thenReturn(TAX_COUNTRIES_RSC_URI);
        when(mocks.req().getHeader(ACCOUNT_PARAM_NAME)).thenReturn("\t");

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(vatinController);
        verify(taxZoneController).listTaxZones(null, tenant);
        assertEquals(mocks.getResponseContentType(), APPLICATION_JSON);
        assertEquals(mocks.getResponseStatus(), SC_OK);
    }

    @Test(groups = "fast", dataProvider = "invalidAccountUUIDs")
    public void shouldRespondBadRequestWhenDispatchingGetTaxZonesWithInvalidAccountRestriction(String accountId)
            throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        withTenant(mocks.req());

        when(mocks.req().getPathInfo()).thenReturn(TAX_COUNTRIES_RSC_URI);
        when(mocks.req().getParameter(ACCOUNT_PARAM_NAME)).thenReturn(accountId);

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_BAD_REQUEST);
    }

    @Test(groups = "fast")
    public void shouldDispatchGetTaxZonesWithAccountRestriction() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn(TAX_COUNTRIES_RSC_URI);
        when(mocks.req().getParameter(ACCOUNT_PARAM_NAME)).thenReturn(accountId.toString());

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(vatinController);
        verify(taxZoneController).listTaxZones(accountId, tenant);
        assertEquals(mocks.getResponseContentType(), APPLICATION_JSON);
        assertEquals(mocks.getResponseStatus(), SC_OK);
    }

    // ==================== GET /vatins?account=... ====================

    @Test(groups = "fast")
    public void shouldDispatchGetVATINs() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        when(mocks.req().getPathInfo()).thenReturn(VATINS_RSC_URI);

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController);
        verify(vatinController).listVatins(null, tenant);
        assertEquals(mocks.getResponseContentType(), APPLICATION_JSON);
        assertEquals(mocks.getResponseStatus(), SC_OK);
    }

    @Test(groups = "fast")
    public void shouldDispatchGetVATINsWhenAccountRestrictionIsBlank() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        when(mocks.req().getPathInfo()).thenReturn(VATINS_RSC_URI);
        when(mocks.req().getHeader(ACCOUNT_PARAM_NAME)).thenReturn("\t");

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController);
        verify(vatinController).listVatins(null, tenant);
        assertEquals(mocks.getResponseContentType(), APPLICATION_JSON);
        assertEquals(mocks.getResponseStatus(), SC_OK);
    }

    @Test(groups = "fast", dataProvider = "invalidAccountUUIDs")
    public void shouldRespondBadRequestWhenDispatchingGetVATINsWithInvalidAccountRestriction(String accountId)
            throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        withTenant(mocks.req());

        when(mocks.req().getPathInfo()).thenReturn(VATINS_RSC_URI);
        when(mocks.req().getParameter(ACCOUNT_PARAM_NAME)).thenReturn(accountId);

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_BAD_REQUEST);
    }

    @Test(groups = "fast")
    public void shouldDispatchGetVATINsWithAccountRestriction() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn(VATINS_RSC_URI);
        when(mocks.req().getParameter(ACCOUNT_PARAM_NAME)).thenReturn(accountId.toString());

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController);
        verify(vatinController).listVatins(accountId, tenant);
        assertEquals(mocks.getResponseContentType(), APPLICATION_JSON);
        assertEquals(mocks.getResponseStatus(), SC_OK);
    }

    // ==================== GET /... ====================

    @Test(groups = "fast")
    public void shouldRespondNotFoundWhenDispatchingGetWithUknownURI() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        withTenant(mocks.req());
        when(mocks.req().getPathInfo()).thenReturn("/plop");

        // When
        servlet.doGet(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_NOT_FOUND);
    }

    // ==================== PUT ====================

    @Test(groups = "fast")
    public void shouldRespondNotFoundWhenDispatchingPutWithNoTenant() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_NOT_FOUND);
    }

    // ===== PUT /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxZone =====

    @Test(groups = "fast", dataProvider = "invalidAccountUUIDs")
    public void shouldRespondNotFoundWhenDispatchingPutAccountResourceWithNoAccount(String accountId) throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        withTenant(mocks.req());
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/taxZone");

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_NOT_FOUND);
    }

    @Test(groups = "fast")
    public void shouldRespondBadRequestWhenDispatchingPutAccountTaxZoneWithNullBody() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/taxZone");
        mocks.withRequestBody("null");

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_BAD_REQUEST);
    }

    @Test(groups = "fast")
    public void shouldRespondInternalServerErrorWhenSavingAccountTaxZoneFails() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/taxZone");
        mocks.withRequestBody("{\"accountId\":\"" + accountId + "\",\"taxZone\":\"" + FR + "\"}");

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(vatinController);
        verify(taxZoneController).saveAccountTaxZone(eq(accountId), taxZoneRsc.capture(), eq(tenant));
        assertEquals(taxZoneRsc.getValue().accountId, accountId);
        assertEquals(taxZoneRsc.getValue().taxZone, FRANCE);
        assertEquals(mocks.getResponseStatus(), SC_INTERNAL_SERVER_ERROR);
    }

    @Test(groups = "fast")
    public void shouldRespondBadRequestWhenDispatchingPutAccountTaxZoneWithInvalidCountry() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/taxZone");
        mocks.withRequestBody("{\"accountId\":\"" + accountId + "\",\"taxZone\":\"KK\"}");

        when(taxZoneController.saveAccountTaxZone(eq(accountId), any(TaxZoneRsc.class), eq(tenant))).thenReturn(true);

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_BAD_REQUEST);
    }

    @Test(groups = "fast")
    public void shouldDispatchPutAccountTaxZone() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/taxZone");
        mocks.withRequestBody("{\"accountId\":\"" + accountId + "\",\"taxZone\":\"" + FR + "\"}");

        when(taxZoneController.saveAccountTaxZone(eq(accountId), any(TaxZoneRsc.class), eq(tenant))).thenReturn(true);

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(vatinController);
        verify(taxZoneController).saveAccountTaxZone(eq(accountId), taxZoneRsc.capture(), eq(tenant));
        assertEquals(taxZoneRsc.getValue().accountId, accountId);
        assertEquals(taxZoneRsc.getValue().taxZone, FRANCE);
        assertEquals(mocks.getResponseStatus(), SC_CREATED);
    }

    // ========== PUT /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/vatin ==========

    @Test(groups = "fast")
    public void shouldRespondBadRequestWhenDispatchingPutAccountVATINWithNullBody() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/vatin");
        mocks.withRequestBody("null");

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_BAD_REQUEST);
    }

    @Test(groups = "fast")
    public void shouldRespondInternalServerErrorWhenSavingAccountVATINFails() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/vatin");
        mocks.withRequestBody("{\"accountId\":\"" + accountId + "\",\"vatin\":\"" + FR_TEST6_VATIN_NUM + "\"}");

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController);
        verify(vatinController).saveAccountVatin(eq(accountId), vatinRsc.capture(), eq(tenant));
        assertEquals(vatinRsc.getValue().accountId, accountId);
        assertEquals(vatinRsc.getValue().vatin, FR_TEST6_VATIN);
        assertEquals(mocks.getResponseStatus(), SC_INTERNAL_SERVER_ERROR);
    }

    @Test(groups = "fast")
    public void shouldRespondBadRequestWhenDispatchingPutAccountVATINWithInvalidVATIN() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/vatin");
        mocks.withRequestBody("{\"accountId\":\"" + accountId + "\",\"vatin\":\"boom!\"}");

        when(vatinController.saveAccountVatin(eq(accountId), any(VATINRsc.class), eq(tenant))).thenReturn(true);

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_BAD_REQUEST);
    }

    @Test(groups = "fast")
    public void shouldDispatchPutAccountVATIN() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        Tenant tenant = withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/vatin");
        mocks.withRequestBody("{\"accountId\":\"" + accountId + "\",\"vatin\":\"" + FR_TEST6_VATIN_NUM + "\"}");

        when(vatinController.saveAccountVatin(eq(accountId), any(VATINRsc.class), eq(tenant))).thenReturn(true);

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController);
        verify(vatinController).saveAccountVatin(eq(accountId), vatinRsc.capture(), eq(tenant));
        assertEquals(vatinRsc.getValue().accountId, accountId);
        assertEquals(vatinRsc.getValue().vatin, FR_TEST6_VATIN);
        assertEquals(mocks.getResponseStatus(), SC_CREATED);
    }

    // ========== PUT /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/... ==========

    @Test(groups = "fast")
    public void shouldRespondNotFoundWhenDispatchingPutAccountWithUnknownResource() throws Exception {
        // Given
        ServletMocks mocks = new ServletMocks();
        withTenant(mocks.req());

        UUID accountId = randomUUID();
        when(mocks.req().getPathInfo()).thenReturn("/accounts/" + accountId + "/plop");

        // When
        servlet.doPut(mocks.req(), mocks.resp());

        // Then
        verifyZeroInteractions(taxZoneController, vatinController);
        assertEquals(mocks.getResponseStatus(), SC_NOT_FOUND);
    }
}
