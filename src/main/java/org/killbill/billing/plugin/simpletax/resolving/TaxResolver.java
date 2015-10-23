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

import javax.annotation.Nullable;

import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;

/**
 * Tax resolvers are meant to apply regulation specific rules when determining
 * which tax code actually applies, in a set of potential candidates.
 * <p>
 * Constructors of implementing classes must accept one single argument of type
 * {@link org.killbill.billing.plugin.simpletax.TaxComputationContext}.
 *
 * @author Benjamin Gandon
 */
public interface TaxResolver {

    /**
     * Retain only one applicable tax code, so that taxation of invoice item can
     * be done, based on the returned tax code.
     *
     * @param taxCodes
     *            The candidate tax codes for this invoice item.
     * @param item
     *            The invoice item to tax.
     * @return The tax code to apply to this item.
     */
    @Nullable
    public abstract TaxCode applicableCodeForItem(Set<TaxCode> taxCodes, InvoiceItem item);

}