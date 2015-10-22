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
package org.killbill.billing.plugin.simpletax.resolving;

import java.util.Set;

import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;

/**
 * A default fail-safe tax resolver that disables taxation altogether.
 *
 * @author Benjamin Gandon
 */
public class NullTaxResolver implements TaxResolver {

    /**
     * The mandatory public constructor for tax resolvers, which accepts a
     * single {@link TaxComputationContext} argument.
     *
     * @param ctx
     *            The tax computation context.
     */
    public NullTaxResolver(TaxComputationContext ctx) {
        super();
    }

    @Override
    public TaxCode applicableCodeForItem(Set<TaxCode> taxCodes, InvoiceItem item) {
        return null;
    }
}
