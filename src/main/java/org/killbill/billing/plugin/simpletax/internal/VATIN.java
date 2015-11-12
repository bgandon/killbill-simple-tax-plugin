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
package org.killbill.billing.plugin.simpletax.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static org.killbill.billing.plugin.simpletax.util.ShortToStringStyle.SHORT_STYLE;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * An immutable holder for type-safe manipulation of <a
 * href="https://en.wikipedia.org/wiki/VAT_identification_number">VAT
 * identification numbers</a>.
 * <p>
 * See also libraries in other languages:
 * <ul>
 * <li><a href="http://www.braemoor.co.uk/software/vat.shtml">JavaScript EU VAT
 * Number Validation</a></li>
 * <li><a href="https://code.google.com/p/vatnumber/">vatnumber, a Python module
 * to validate VAT numbers</a></li>
 * <li><a href="https://github.com/ddeboer/vatin">Validate VAT identification
 * numbers (PHP)</a></li>
 * <li><a href="https://github.com/viruschidai/validate-vat">A European VAT
 * number validating lib (CoffeeScript)</a></li>
 * </ul>
 * Online validators:
 * <ul>
 * <li><a href="http://vatid.eu/">Free EU VAT Number Validator (REST
 * endpoint)</a></li>
 * <li><a href="https://github.com/SubOptimal/libevatr">Java library to validate
 * VAT ID numbers</a></li>
 * </ul>
 *
 * @author Benjamin Gandon
 */
public class VATIN {

    private static final VATINValidator VALIDATOR = new VATINValidator();

    private String number;

    /**
     * @param number
     *            A valid VAT Identificaiton Number.
     * @throws IllegalArgumentException
     *             When the VAT number is invalid.
     */
    @JsonCreator
    public VATIN(String number) throws IllegalArgumentException {
        super();
        checkArgument(VALIDATOR.apply(number), "Illegal VAT Identification Number: [%s]", number);
        this.number = number;
    }

    /**
     * @return The VAT Identificaiton Number.
     */
    @JsonValue
    public String getNumber() {
        return number;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        VATIN rhs = (VATIN) obj;
        return new EqualsBuilder().append(number, rhs.number).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(number).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_STYLE)//
                .append("number", number)//
                .toString();
    }
}
