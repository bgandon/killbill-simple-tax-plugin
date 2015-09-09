package org.killbill.billing.plugin.simpletax;

import java.util.Hashtable;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.plugin.api.notification.PluginConfigurationEventHandler;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.killbill.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;
import org.osgi.framework.BundleContext;

/**
 * Activator class for the Simple Tax Plugin.
 *
 * @author Benjamin Gandon
 */
public class SimpleTaxActivator extends KillbillActivatorBase {

    public static final String PLUGIN_NAME = "killbill-simple-tax";

    @Override
    public OSGIKillbillEventHandler getOSGIKillbillEventHandler() {
        return new PluginConfigurationEventHandler();
    }

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        Clock clock = new DefaultClock();

        InvoicePluginApi plugin = new SimpleTaxInvoicePluginApi(killbillAPI, configProperties, logService, clock);
        registerInvoicePluginApi(context, plugin);
    }

    private void registerInvoicePluginApi(final BundleContext context, final InvoicePluginApi api) {
        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, InvoicePluginApi.class, api, props);
    }
}
