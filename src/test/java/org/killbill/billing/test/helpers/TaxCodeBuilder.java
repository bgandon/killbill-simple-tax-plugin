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
package org.killbill.billing.test.helpers;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.killbill.billing.plugin.simpletax.internal.TaxZone;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;

@SuppressWarnings("javadoc")
public class TaxCodeBuilder implements Builder<TaxCode> {

    private String name;
    private String taxItemDescription;
    private BigDecimal rate;
    private LocalDate startingOn;
    private LocalDate stoppingOn;
    private TaxZone taxZone;

    @Override
    public TaxCode build() {
        return new TaxCode(name, taxItemDescription, rate, startingOn, stoppingOn, taxZone);
    }

    public TaxCodeBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public TaxCodeBuilder withTaxItemDescription(String taxItemDescription) {
        this.taxItemDescription = taxItemDescription;
        return this;
    }

    public TaxCodeBuilder withRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public TaxCodeBuilder withStartingOn(LocalDate startingOn) {
        this.startingOn = startingOn;
        return this;
    }

    public TaxCodeBuilder withStoppingOn(LocalDate stoppingOn) {
        this.stoppingOn = stoppingOn;
        return this;
    }

    public TaxCodeBuilder withTaxZone(TaxZone taxZone) {
        this.taxZone = taxZone;
        return this;
    }

}
