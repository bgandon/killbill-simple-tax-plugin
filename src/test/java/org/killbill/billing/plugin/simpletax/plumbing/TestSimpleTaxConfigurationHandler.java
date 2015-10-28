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

import static java.math.BigDecimal.ZERO;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

import java.util.Properties;

import org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig;
import org.killbill.billing.test.helpers.TaxCodeBuilder;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;
import org.mockito.Mock;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for {@link SimpleTaxConfigurationHandler}.
 * 
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestSimpleTaxConfigurationHandler {

    @Mock
    private OSGIKillbillAPI services;
    @Mock
    private OSGIKillbillLogService logService;

    private SimpleTaxConfigurationHandler configHandler;

    @BeforeClass
    public void init() {
        initMocks(this);
        configHandler = new SimpleTaxConfigurationHandler("pluginName", services, logService);
    }

    @Test(groups = "fast")
    public void shouldCreateConfigurable() {
        // Given
        Properties pluginConfig = new Properties();
        pluginConfig.put("org.killbill.billing.plugin.simpletax.taxCodes.plop", "");

        // When
        SimpleTaxConfig config = configHandler.createConfigurable(pluginConfig);

        // Then
        assertEquals(config.findTaxCode("plop"), new TaxCodeBuilder()//
                .withName("plop")//
                .withTaxItemDescription("tax")//
                .withRate(ZERO)//
                .build());
    }
}
