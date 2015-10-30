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
package org.killbill.billing.plugin.simpletax.resolving.fixtures;

import java.math.BigDecimal;

import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.resolving.NullTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.TaxResolver;
import org.killbill.billing.test.helpers.TaxCodeBuilder;

/**
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public abstract class AbstractTaxResolver implements TaxResolver {

    protected static final TaxCode TAX_CODE = new TaxCodeBuilder().withRate(new BigDecimal("0.0314")).build();

    public AbstractTaxResolver(TaxComputationContext ctx) {
    }

    /**
     * We return a non-zero tax rate here in order to differentiate the tax
     * resolvers of this hierarchy from the {@link NullTaxResolver}.
     */
    @Override
    public TaxCode applicableCodeForItem(Iterable<TaxCode> taxCodes, InvoiceItem item) {
        return TAX_CODE;
    }
}