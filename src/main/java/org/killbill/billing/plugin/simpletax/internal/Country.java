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
import static java.util.Locale.getISOCountries;
import static org.killbill.billing.plugin.simpletax.util.ShortToStringStyle.SHORT_STYLE;

import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.killbill.billing.plugin.simpletax.util.LazyValue;

import com.google.common.collect.ImmutableSet;

/**
 * An immutable country, based on ISO 3166-1 alpha-2 standard. This class helps
 * in manipulating consistent, predictable and type-safe country codes.
 *
 * @author Benjamin Gandon
 */
public class Country {

    private static final LazyValue<Set<String>, RuntimeException> COUNTRIES = new LazyValue<Set<String>, RuntimeException>() {
        @Override
        protected Set<String> initialize() throws RuntimeException {
            return ImmutableSet.copyOf(getISOCountries());
        }
    };

    private String code;

    /**
     * Constructs a new country. The country code must be an element of
     * {@link Locale#getISOCountries()}.
     *
     * @param code
     *            The ISO 3166-1 alpha-2 country code.
     * @throws IllegalArgumentException
     *             when the country code is not an element of
     *             {@link Locale#getISOCountries()}.
     */
    public Country(String code) throws IllegalArgumentException {
        checkArgument(COUNTRIES.get().contains(code), "Illegal country code: %s", code);
        this.code = code;
    }

    /**
     * Returns the ISO 3166-1 alpha-2 code for this country.
     *
     * @return The code of this country. Never {@code null}.
     */
    public String getCode() {
        return code;
    }

    /**
     * Computes the name of this country, in the specified language, or in
     * English if the language is not {@linkplain Locale#getAvailableLocales()
     * supported}.
     *
     * @param language
     *            The preferred language in which the country name should be
     *            expressed.
     * @return The name of the country in the specified language, or in English.
     */
    public String computeName(Locale language) {
        return new Locale("", code).getDisplayCountry(language);
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
        Country rhs = (Country) obj;
        return new EqualsBuilder().append(code, rhs.code).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(code).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_STYLE)//
                .append("code", code)//
                .toString();
    }
}