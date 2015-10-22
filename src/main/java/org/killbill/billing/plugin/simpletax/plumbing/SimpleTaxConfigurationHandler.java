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

import java.util.Map;
import java.util.Properties;

import org.killbill.billing.plugin.api.notification.PluginTenantConfigurableConfigurationHandler;
import org.killbill.billing.plugin.simpletax.SimpleTaxConfig;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.collect.Maps;

/**
 * A per-tenant configuration handler for the simple-tax plugin.
 *
 * @author Benjamin Gandon
 */
public class SimpleTaxConfigurationHandler extends PluginTenantConfigurableConfigurationHandler<SimpleTaxConfig> {

    private OSGIKillbillLogService logService;

    /**
     * Constructs a new configuration handler.
     *
     * @param pluginName
     *            The plugin name to use when accessing per-tenant
     *            configuration.
     * @param services
     *            The Kill Bill meta-API.
     * @param logService
     *            The service to use when logging events.
     */
    public SimpleTaxConfigurationHandler(String pluginName, OSGIKillbillAPI services, OSGIKillbillLogService logService) {
        super(pluginName, services, logService);
        this.logService = logService;
    }

    @Override
    protected SimpleTaxConfig createConfigurable(Properties pluginConfig) {
        Map<String, String> props = Maps.fromProperties(pluginConfig);
        return new SimpleTaxConfig(props, logService);
    }
}
