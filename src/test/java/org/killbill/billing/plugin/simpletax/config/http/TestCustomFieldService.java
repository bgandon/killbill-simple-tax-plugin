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

import static com.google.common.collect.Iterators.forArray;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.contains;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.killbill.billing.ErrorCode.UNEXPECTED_ERROR;
import static org.killbill.billing.ObjectType.ACCOUNT;
import static org.killbill.billing.ObjectType.INVOICE;
import static org.killbill.billing.test.helpers.CustomFieldBuilder.copy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.osgi.service.log.LogService.LOG_ERROR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.test.helpers.CustomFieldBuilder;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillLogService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.Lists;

/**
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestCustomFieldService {

    private static final long PAGE_SIZE = 100L;

    @Mock
    private CustomFieldUserApi customFieldApi;
    @Mock
    private OSGIKillbillLogService logService;

    @InjectMocks
    private CustomFieldService service;

    @Captor
    private ArgumentCaptor<List<CustomField>> addedFields;
    @Captor
    private ArgumentCaptor<List<CustomField>> removedFields;

    @Mock
    private TenantContext defaultTenant;
    @Mock
    private Pagination<CustomField> pageStart;
    @Mock
    private Pagination<CustomField> pageMiddle;
    @Mock
    private Pagination<CustomField> pageEnd;

    @BeforeClass
    public void init() {
        initMocks(this);
    }

    private void withThreePagesOfSearchResults(TenantContext tenantContext) {
        when(customFieldApi.searchCustomFields(anyString(), eq(0L), eq(PAGE_SIZE), eq(tenantContext)))//
                .thenReturn(pageStart);
        when(customFieldApi.searchCustomFields(anyString(), eq(PAGE_SIZE), eq(PAGE_SIZE), eq(tenantContext)))//
                .thenReturn(pageMiddle);
        when(customFieldApi.searchCustomFields(anyString(), eq(2 * PAGE_SIZE), eq(PAGE_SIZE), eq(tenantContext)))//
                .thenReturn(pageEnd);

        when(pageStart.getNextOffset()).thenReturn(PAGE_SIZE);
        when(pageMiddle.getNextOffset()).thenReturn(2 * PAGE_SIZE);
        when(pageEnd.getNextOffset()).thenReturn(null);

        CustomFieldBuilder builder = new CustomFieldBuilder().withObjectType(ACCOUNT).withFieldName("toto");
        when(pageStart.iterator()).thenReturn(forArray(//
                copy(builder).withFieldValue("page0-invoice").withObjectType(INVOICE).build(),//
                copy(builder).withFieldValue("page0-account").build()));
        when(pageMiddle.iterator()).thenReturn(forArray(//
                copy(builder).withFieldValue("page1-toto").build(),//
                copy(builder).withFieldValue("page1-plop").withFieldName("plop").build()));
        when(pageEnd.iterator()).thenReturn(forArray(//
                copy(builder).withFieldValue("page2").build()));
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotAcceptFindingFieldsWithNullName() {
        // Expect exception
        service.findAllAccountFieldsByFieldNameAndTenant(null, defaultTenant);
    }

    @Test(groups = "fast")
    public void shouldFindAccountFields() {
        // Given
        TenantContext tenant = mock(TenantContext.class);
        withThreePagesOfSearchResults(tenant);

        // When
        List<CustomField> fields = service.findAllAccountFieldsByFieldNameAndTenant("toto", tenant);

        // Then
        assertEquals(fields.size(), 3);

        CustomField field1 = fields.get(0);
        assertNotNull(field1);
        assertEquals(field1.getFieldName(), "toto");
        assertEquals(field1.getFieldValue(), "page0-account");

        CustomField field2 = fields.get(1);
        assertNotNull(field2);
        assertEquals(field2.getFieldName(), "toto");
        assertEquals(field2.getFieldValue(), "page1-toto");

        CustomField field3 = fields.get(2);
        assertNotNull(field3);
        assertEquals(field3.getFieldName(), "toto");
        assertEquals(field3.getFieldValue(), "page2");
    }

    private void withAccountFields(List<CustomField> fields, TenantContext tenant) {
        when(customFieldApi.getCustomFieldsForObject(any(UUID.class), any(ObjectType.class), eq(tenant)))//
                .thenReturn(fields);
    }

    @Test(groups = "fast", expectedExceptions = NullPointerException.class)
    public void shouldNotAcceptFindingSingleFieldWithNullName() {

        // Expect exception
        service.findFieldByNameAndAccountAndTenant(null, randomUUID(), defaultTenant);
    }

    @Test(groups = "fast")
    public void shouldAcceptNullListWhenFindingAccountField() {
        // Given
        TenantContext tenant = mock(TenantContext.class);
        withAccountFields(null, tenant);

        // Expect
        assertNull(service.findFieldByNameAndAccountAndTenant("plop", randomUUID(), tenant));
    }

    @Test(groups = "fast")
    public void shouldFindAccountField() {
        // Given
        TenantContext tenant = mock(TenantContext.class);
        withAccountFields(
                newArrayList(//
                        new CustomFieldBuilder()//
                                .withObjectType(ACCOUNT)//
                                .withFieldName("toto")//
                                .build(),//
                        new CustomFieldBuilder()//
                                .withObjectType(ACCOUNT)//
                                .withFieldName("plop")//
                                .withFieldValue("bingo!")//
                                .build(),//
                        new CustomFieldBuilder()//
                                .withObjectType(ACCOUNT)//
                                .withFieldName("plop")//
                                .withFieldValue(
                                        "uh oh.. duplicate fields are illegal"
                                                + " but we should return the first match anyway")//
                                .build()), tenant);

        // When
        CustomField field = service.findFieldByNameAndAccountAndTenant("plop", randomUUID(), tenant);

        // Then
        assertNotNull(field);
        assertEquals(field.getObjectType(), ACCOUNT);
        assertEquals(field.getFieldName(), "plop");
        assertEquals(field.getFieldValue(), "bingo!");
    }

    @Test(groups = "fast")
    public void shouldSaveFieldWhenNoneAlreadyExists() throws Exception {
        // Given
        UUID accountId = randomUUID();

        // When
        boolean ok = service.saveAccountField("titi", "toto", accountId, defaultTenant);

        // Then
        assertTrue(ok);
        verify(customFieldApi).addCustomFields(addedFields.capture(), any(CallContext.class));
        assertNotNull(addedFields.getValue());
        assertEquals(addedFields.getValue().size(), 1);

        CustomField addedField = addedFields.getValue().get(0);
        assertNotNull(addedField);
        assertEquals(addedField.getObjectType(), ACCOUNT);
        assertEquals(addedField.getObjectId(), accountId);
        assertEquals(addedField.getFieldName(), "toto");
        assertEquals(addedField.getFieldValue(), "titi");

        verifyZeroInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldSaveFieldWhenNoneExistsWithoutCloberingAnyOther() throws Exception {
        // Given
        CustomFieldUserApi customFieldApi = mock(CustomFieldUserApi.class);
        CustomFieldService service = new CustomFieldService(customFieldApi, logService);

        when(customFieldApi.getCustomFieldsForObject(any(UUID.class), any(ObjectType.class), any(TenantContext.class)))//
                .thenReturn(newArrayList(new CustomFieldBuilder()//
                        .withFieldName("plop")//
                        .build()));

        UUID accountId = randomUUID();
        when(customFieldApi.getCustomFieldsForObject(accountId, ACCOUNT, defaultTenant))//
                .thenReturn(Lists.<CustomField> newArrayList());

        // When
        boolean ok = service.saveAccountField("titi", "plop", accountId, defaultTenant);

        // Then
        assertTrue(ok);
        verify(customFieldApi, never()).removeCustomFields(anyListOf(CustomField.class), any(CallContext.class));

        verify(customFieldApi, times(1)).addCustomFields(addedFields.capture(), any(CallContext.class));
        assertNotNull(addedFields.getValue());
        assertEquals(addedFields.getValue().size(), 1);

        verifyZeroInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldModifyExistingField() throws Exception {
        // Given
        CustomFieldUserApi customFieldApi = mock(CustomFieldUserApi.class);
        OSGIKillbillLogService logService = mock(OSGIKillbillLogService.class);
        CustomFieldService service = new CustomFieldService(customFieldApi, logService);

        UUID accountId = randomUUID();
        when(customFieldApi.getCustomFieldsForObject(accountId, ACCOUNT, defaultTenant))//
                .thenReturn(newArrayList(new CustomFieldBuilder()//
                        .withFieldName("plop")//
                        .withFieldValue("boom")//
                        .build(), new CustomFieldBuilder()//
                        .withFieldName("toto")//
                        .withFieldValue("plip")//
                        .build()));

        // When
        boolean ok = service.saveAccountField("titi", "toto", accountId, defaultTenant);

        // Then
        assertTrue(ok);
        verify(customFieldApi).removeCustomFields(removedFields.capture(), any(CallContext.class));
        assertNotNull(removedFields.getValue());
        assertEquals(removedFields.getValue().size(), 1);

        CustomField removedField = removedFields.getValue().get(0);
        assertNotNull(removedField);
        assertEquals(removedField.getFieldName(), "toto");
        assertEquals(removedField.getFieldValue(), "plip");

        verify(customFieldApi).addCustomFields(addedFields.capture(), any(CallContext.class));
        assertNotNull(addedFields.getValue());
        assertEquals(addedFields.getValue().size(), 1);

        CustomField addedField = addedFields.getValue().get(0);
        assertNotNull(addedField);
        assertEquals(addedField.getObjectType(), ACCOUNT);
        assertEquals(addedField.getObjectId(), accountId);
        assertEquals(addedField.getFieldName(), "toto");
        assertEquals(addedField.getFieldValue(), "titi");

        verifyZeroInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldSurviveNullListOfExistingFields() throws Exception {
        // Given
        CustomFieldUserApi customFieldApi = mock(CustomFieldUserApi.class);
        CustomFieldService service = new CustomFieldService(customFieldApi, logService);

        UUID accountId = randomUUID();
        when(customFieldApi.getCustomFieldsForObject(accountId, ACCOUNT, defaultTenant))//
                .thenReturn(null);

        // When
        boolean ok = service.saveAccountField("titi", "toto", accountId, defaultTenant);

        // Then
        assertTrue(ok);
        verify(customFieldApi).addCustomFields(addedFields.capture(), any(CallContext.class));
        assertEquals(addedFields.getValue().size(), 1);

        verifyZeroInteractions(logService);
    }

    @Test(groups = "fast")
    public void shouldSurviveExceptionWhenRemovingField() throws Exception {
        // Given
        CustomFieldUserApi customFieldApi = mock(CustomFieldUserApi.class);
        OSGIKillbillLogService logService = mock(OSGIKillbillLogService.class);
        CustomFieldService service = new CustomFieldService(customFieldApi, logService);

        UUID accountId = randomUUID();
        when(customFieldApi.getCustomFieldsForObject(accountId, ACCOUNT, defaultTenant))//
                .thenReturn(newArrayList(new CustomFieldBuilder()//
                        .withObjectType(ACCOUNT)//
                        .withObjectId(accountId)//
                        .withFieldName("toto")//
                        .withFieldValue("tata")//
                        .build()));

        doThrow(CustomFieldApiException.class)//
                .when(customFieldApi).removeCustomFields(anyListOf(CustomField.class), any(CallContext.class));

        // When
        boolean ok = service.saveAccountField("titi", "toto", accountId, defaultTenant);

        // Then
        assertFalse(ok);
        verify(logService).log(eq(LOG_ERROR),//
                argThat(allOf(containsString("toto"),//
                        containsString("tata"),//
                        containsString(accountId.toString()))),//
                any(CustomFieldApiException.class));
    }

    @Test(groups = "fast")
    public void shouldSurviveExceptionWhenAddingField() throws Exception {
        // Given
        CustomFieldUserApi customFieldApi = mock(CustomFieldUserApi.class);
        OSGIKillbillLogService logService = mock(OSGIKillbillLogService.class);
        CustomFieldService service = new CustomFieldService(customFieldApi, logService);

        UUID accountId = randomUUID();
        when(customFieldApi.getCustomFieldsForObject(accountId, ACCOUNT, defaultTenant))//
                .thenReturn(newArrayList(new CustomFieldBuilder()//
                        .withObjectType(ACCOUNT)//
                        .withObjectId(accountId)//
                        .withFieldName("toto")//
                        .withFieldValue("tata")//
                        .build()));

        doAnswer(new Answer<Void>() {
            private boolean first = true;

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                if (first) {
                    first = false;
                    throw new CustomFieldApiException(UNEXPECTED_ERROR, "test");
                }
                return null;
            }
        }).when(customFieldApi).addCustomFields(anyListOf(CustomField.class), any(CallContext.class));

        // When
        boolean ok = service.saveAccountField("titi", "toto", accountId, defaultTenant);

        // Then
        assertFalse(ok);
        InOrder inOrder = inOrder(customFieldApi);

        inOrder.verify(customFieldApi).removeCustomFields(removedFields.capture(), any(CallContext.class));
        assertNotNull(removedFields.getValue());
        assertEquals(removedFields.getValue().size(), 1);

        CustomField removedField = removedFields.getValue().get(0);
        assertNotNull(removedField);
        assertEquals(removedField.getFieldName(), "toto");
        assertEquals(removedField.getFieldValue(), "tata");

        inOrder.verify(customFieldApi, times(2)).addCustomFields(addedFields.capture(), any(CallContext.class));
        assertNotNull(addedFields.getValue());
        assertEquals(addedFields.getValue().size(), 1);

        CustomField addedField = addedFields.getValue().get(0);
        assertNotNull(addedField);
        assertEquals(addedField.getObjectType(), ACCOUNT);
        assertEquals(addedField.getObjectId(), accountId);
        assertEquals(addedField.getFieldName(), "toto");
        assertEquals(addedField.getFieldValue(), "tata");

        verify(logService).log(eq(LOG_ERROR),//
                argThat(allOf(containsString("toto"),//
                        containsString("titi"),//
                        containsString(accountId.toString()))),//
                any(CustomFieldApiException.class));
    }

    @Test(groups = "fast")
    public void shouldSurviveExceptionsWhenAddingField() throws Exception {
        // Given
        CustomFieldUserApi customFieldApi = mock(CustomFieldUserApi.class);
        OSGIKillbillLogService logService = mock(OSGIKillbillLogService.class);
        CustomFieldService service = new CustomFieldService(customFieldApi, logService);

        UUID accountId = randomUUID();
        when(customFieldApi.getCustomFieldsForObject(accountId, ACCOUNT, defaultTenant))//
                .thenReturn(newArrayList(new CustomFieldBuilder()//
                        .withObjectType(ACCOUNT)//
                        .withObjectId(accountId)//
                        .withFieldName("toto")//
                        .withFieldValue("tata")//
                        .build()));

        doThrow(new CustomFieldApiException(UNEXPECTED_ERROR, "test"))//
                .when(customFieldApi).addCustomFields(anyListOf(CustomField.class), any(CallContext.class));

        // When
        boolean ok = service.saveAccountField("titi", "toto", accountId, defaultTenant);

        // Then
        assertFalse(ok);
        InOrder inOrder = inOrder(customFieldApi);

        inOrder.verify(customFieldApi).removeCustomFields(removedFields.capture(), any(CallContext.class));
        assertNotNull(removedFields.getValue());
        assertEquals(removedFields.getValue().size(), 1);

        CustomField removedField = removedFields.getValue().get(0);
        assertNotNull(removedField);
        assertEquals(removedField.getFieldName(), "toto");
        assertEquals(removedField.getFieldValue(), "tata");

        inOrder.verify(customFieldApi, times(2)).addCustomFields(addedFields.capture(), any(CallContext.class));
        assertNotNull(addedFields.getValue());
        assertEquals(addedFields.getValue().size(), 1);

        CustomField addedField = addedFields.getValue().get(0);
        assertNotNull(addedField);
        assertEquals(addedField.getObjectType(), ACCOUNT);
        assertEquals(addedField.getObjectId(), accountId);
        assertEquals(addedField.getFieldName(), "toto");
        assertEquals(addedField.getFieldValue(), "tata");

        ArgumentCaptor<String> errMsg = ArgumentCaptor.forClass(String.class);
        verify(logService, times(2)).log(eq(LOG_ERROR),//
                errMsg.capture(),//
                any(CustomFieldApiException.class));
        String errMsg1 = errMsg.getAllValues().get(0);
        assertTrue(contains(errMsg1, "toto"));
        assertTrue(contains(errMsg1, "titi"));
        assertTrue(contains(errMsg1, accountId.toString()));

        String errMsg2 = errMsg.getAllValues().get(1);
        assertTrue(contains(errMsg2, "toto"));
        assertTrue(contains(errMsg2, "tata"));
        assertTrue(contains(errMsg2, accountId.toString()));
    }
}
