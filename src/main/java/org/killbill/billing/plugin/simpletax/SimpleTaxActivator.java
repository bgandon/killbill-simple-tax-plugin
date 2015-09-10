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

import static org.killbill.billing.osgi.api.OSGIPluginProperties.PLUGIN_NAME_PROP;

import java.util.Hashtable;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.plugin.api.notification.PluginConfigurationEventHandler;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.killbill.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.killbill.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;
import org.osgi.framework.BundleContext;

/**
 * Activator class for the Simple Tax Plugin.
 *
 * @author Benjamin Gandon
 */
public class SimpleTaxActivator extends KillbillActivatorBase {

    public static final String PLUGIN_NAME = "killbill-simple-tax";

    public static final String PROPERTY_PREFIX = "org.killbill.billing.plugin.simpletax.";

    private SimpleTaxConfigurationHandler configHandler;

    /**
     * This method is the first to be called.
     * <p>
     * It creates the configuration “handler” that will <em>manage</em> all the
     * plugin configuration lifecycle (create, reconfigure, destroy), supporting
     * the per-tenant nature of these configurations, with appropriate defaults.
     *
     * @see org.killbill.killbill.osgi.libs.killbill.KillbillActivatorBase#getOSGIKillbillEventHandler()
     */
    @Override
    public OSGIKillbillEventHandler getOSGIKillbillEventHandler() {
        configHandler = new SimpleTaxConfigurationHandler(PLUGIN_NAME, killbillAPI, logService);
        return new PluginConfigurationEventHandler(configHandler);
    }

    /**
     * This method is called after {@link #getOSGIKillbillEventHandler}.
     * <p>
     * It creates a configuration manager, creates the plugin, and then
     * registers it into the system.
     * <p>
     * {@inheritDoc}
     *
     * @see org.killbill.killbill.osgi.libs.killbill.KillbillActivatorBase#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        createDefaultConfig();

        InvoicePluginApi plugin = createPlugin();

        registerInvoicePluginApi(context, plugin);
    }

    /**
     * Creates the {@linkplain #configHandler configuration manager} (called
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
        SimpleTaxPluginConfig defaultConfig = configHandler.createConfigurable(getConfigService().getProperties());
        configHandler.setDefaultConfigurable(defaultConfig);
    }

    private InvoicePluginApi createPlugin() {
        Clock clock = new DefaultClock();
        return new SimpleTaxInvoicePluginApi(configHandler, killbillAPI, getConfigService(), logService, clock);
    }

    private void registerInvoicePluginApi(final BundleContext context, final InvoicePluginApi plugin) {
        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, InvoicePluginApi.class, plugin, props);
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
