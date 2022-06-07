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

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.plugin.api.notification.PluginConfigurationEventHandler;
import org.killbill.billing.plugin.simpletax.SimpleTaxPlugin;
import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.plugin.simpletax.config.http.*;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import java.util.Hashtable;

import static org.killbill.billing.osgi.api.OSGIPluginProperties.PLUGIN_NAME_PROP;

/**
 * Activator class for the Simple Tax Plugin.
 *
 * @author Benjamin Gandon
 */
public class SimpleTaxActivator extends KillbillActivatorBase {

    private static final Logger logger = LoggerFactory.getLogger(SimpleTaxActivator.class);

    /** The name for this plugin. */
    public static final String PLUGIN_NAME = "killbill-simple-tax";

    private SimpleTaxConfigurationHandler simpleTaxConfigHandler;

    /**
     * This method is the first to be called.
     * <p>
     * It creates a configuration manager, creates the plugin, and then
     * registers it into the system.
     * <p>
     * {@inheritDoc}
     *
     * @see org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(BundleContext context) throws Exception {
        // Note: super.start() creates the configHandler that we later use in
        // createDefaultConfig() below
        super.start(context);

        logger.info("starting bundle activator");

        createDefaultConfig();
        CustomFieldService customFieldService = createCustomFieldService();

        final SimpleTaxPlugin plugin = createPlugin(customFieldService);
        final PluginConfigurationEventHandler configHandler = new PluginConfigurationEventHandler(simpleTaxConfigHandler);
        register(InvoicePluginApi.class, plugin, context);        
        dispatcher.registerEventHandlers(configHandler, plugin);
        //;registerEventHandler(plugin);

        InvoiceService invoiceService = createInvoiceService();
        HttpServlet servlet = createServlet(customFieldService, invoiceService);
        register(Servlet.class, servlet, context);
    }

    // /**
    //  * This method is called by {@link KillbillActivatorBase#start}.
    //  * <p>
    //  * It creates the configuration “handler” that will <em>manage</em> all the
    //  * plugin configuration lifecycle (create, reconfigure, destroy), supporting
    //  * the per-tenant nature of these configurations, with appropriate defaults.
    //  * @return PluginConfigurationEventHandler
    //  *
    //  */
    // public OSGIKillbillEventDispatcher.OSGIKillbillEventHandler getOSGIKillbillEventHandler() {
    //     configHandler = new SimpleTaxConfigurationHandler(PLUGIN_NAME, killbillAPI);
    //     return new PluginConfigurationEventHandler(configHandler);
    // }

    /**
     * Creates the {@linkplain #simpleTaxConfigHandler configuration manager} (called
     * “config handler”) and setup the default plugin configuration.
     * <p>
     * At plugin startup time, the default configuration is based on what might
     * have been configured with Java system properties.
     * <p>
     * Later on, the plugin will access any per-tenant configuration that might
     * have been uploaded into the database, with the use of the created
     * configuration manager (a.k.a. “config handler”).
     */
    private void createDefaultConfig() {
        simpleTaxConfigHandler = new SimpleTaxConfigurationHandler(PLUGIN_NAME, killbillAPI);
        SimpleTaxConfig defaultConfig = simpleTaxConfigHandler.createConfigurable(getConfigService().getProperties());
        simpleTaxConfigHandler.setDefaultConfigurable(defaultConfig);
    }

    private CustomFieldService createCustomFieldService() {
        return new CustomFieldService(killbillAPI.getCustomFieldUserApi());
    }

    private InvoiceService createInvoiceService() {
        return new InvoiceService(killbillAPI.getInvoiceUserApi());
    }

    private SimpleTaxPlugin createPlugin(CustomFieldService customFieldService) {
        Clock clock = new DefaultClock();
        return new SimpleTaxPlugin(simpleTaxConfigHandler, customFieldService, killbillAPI, getConfigService(), clock);
    }

    private HttpServlet createServlet(CustomFieldService customFieldService, InvoiceService invoiceService) {
        TaxCountryController taxCountryController = new TaxCountryController(customFieldService);
        VatinController vatinController = new VatinController(customFieldService);
        TaxCodeController taxCodeController = new TaxCodeController(customFieldService, invoiceService);
        return new SimpleTaxServlet(vatinController, taxCountryController, taxCodeController);
    }

    private <S> void register(Class<S> serviceClass, S serviceInstance, BundleContext context) {
        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, serviceClass, serviceInstance, props);
    }

    /**
     * Convenience method used in order to improve the code readability.
     *
     * @return the configuration service
     */
    private OSGIConfigPropertiesService getConfigService() {
        return configProperties;
    }
}
