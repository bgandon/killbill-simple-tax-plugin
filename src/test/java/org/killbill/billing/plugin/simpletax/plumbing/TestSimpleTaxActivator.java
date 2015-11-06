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
package org.killbill.billing.plugin.simpletax.plumbing;

import static org.killbill.billing.osgi.api.OSGIPluginProperties.PLUGIN_NAME_PROP;
import static org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig.PROPERTY_PREFIX;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Hashtable;
import java.util.Observable;
import java.util.Properties;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.api.OSGIConfigProperties;
import org.killbill.billing.osgi.api.OSGIKillbill;
import org.killbill.billing.plugin.simpletax.SimpleTaxPlugin;
import org.killbill.billing.plugin.simpletax.resolving.NullTaxResolver;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for {@link SimpleTaxActivator}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestSimpleTaxActivator {

    @Mock
    private BundleContext context;

    private Observable observableService = new Observable();
    @Mock(answer = RETURNS_DEEP_STUBS)
    private OSGIConfigProperties configPropsService;
    @Mock
    private OSGIKillbill killbillMetaAPIService;

    private SimpleTaxActivator activator = new SimpleTaxActivator();

    @Captor
    private ArgumentCaptor<SimpleTaxPlugin> plugin;
    @Captor
    private ArgumentCaptor<Hashtable<String, String>> props;

    @BeforeClass
    public void init() throws InvalidSyntaxException {
        initMocks(this);

        mockService(Observable.class, observableService);
        mockService(OSGIConfigProperties.class, configPropsService);
        mockService(OSGIKillbill.class, killbillMetaAPIService);

        setupMinimalConfigProps();
    }

    /** Helper method that mocks an OSGi service. */
    private <S> void mockService(Class<S> clazz, S serviceInstance) throws InvalidSyntaxException {
        @SuppressWarnings("unchecked")
        ServiceReference<S> serviceRef = mock(ServiceReference.class);
        when(context.getServiceReferences(clazz.getName(), null)).thenReturn(new ServiceReference<?>[] { serviceRef });
        when(context.getService(serviceRef)).thenReturn(serviceInstance);
    }

    private void setupMinimalConfigProps() {
        Properties minimalNonComplainingConfig = new Properties();
        minimalNonComplainingConfig.put(PROPERTY_PREFIX + "taxResolver", NullTaxResolver.class.getName());
        when(configPropsService.getProperties()).thenReturn(minimalNonComplainingConfig);
    }

    @Test(groups = "fast")
    public void shouldStart() throws Exception {
        // When
        activator.start(context);

        // Then
        assertNotNull(activator.getOSGIKillbillEventHandler());

        verify(context).registerService(eq(InvoicePluginApi.class.getName()), plugin.capture(), props.capture());

        assertNotNull(plugin.getValue());
        assertNotNull(props.getValue());
        assertEquals(props.getValue().get(PLUGIN_NAME_PROP), "killbill-simple-tax");

    }
}
