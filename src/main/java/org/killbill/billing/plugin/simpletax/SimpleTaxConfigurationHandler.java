package org.killbill.billing.plugin.simpletax;

import java.util.Properties;

import org.killbill.billing.plugin.api.notification.PluginTenantConfigurableConfigurationHandler;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class SimpleTaxConfigurationHandler extends PluginTenantConfigurableConfigurationHandler<SimpleTaxPluginConfig> {

    public SimpleTaxConfigurationHandler(final String pluginName, final OSGIKillbillAPI osgiKillbillAPI,
            final OSGIKillbillLogService osgiKillbillLogService) {
        super(pluginName, osgiKillbillAPI, osgiKillbillLogService);
    }

    @Override
    protected SimpleTaxPluginConfig createConfigurable(final Properties pluginConfig) {
        return new SimpleTaxPluginConfig(pluginConfig);
    }

}
