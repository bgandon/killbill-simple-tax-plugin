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
import static org.apache.commons.lang3.StringUtils.containsWhitespace;
import static org.killbill.billing.plugin.simpletax.util.ShortToStringStyle.SHORT_STYLE;

import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.killbill.billing.plugin.simpletax.util.ConcurrentLazyValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;

/**
 * An immutable tax zone, based on country codes, as defined in the ISO 3166-1
 * alpha-2 standard. This class helps in manipulating consistent, predictable
 * and type-safe tax zones.
 *
 * @author Benjamin Gandon
 */
public class TaxZone {

    private static final Supplier<Set<String>> COUNTRIES = new ConcurrentLazyValue<Set<String>>() {
        @Override
        protected Set<String> initialize() {
            return ImmutableSet.copyOf(getISOCountries());
        }
    };

    private static final char SEPARATOR = '_';

    /**
     * Extracts the country code from a tax zone code.
     *
     * @param code
     *            A tax zone code. Must not be {@code null}, nor empty.
     * @return the Country code for the zone code.
     */
    private static String extractCountryPart(String code) {
        String[] parts = StringUtils.split(code, SEPARATOR);
        return parts[0];
    }

    private String code;

    /**
     * Constructs a new tax zone. The zone code must start with an element of
     * {@link Locale#getISOCountries()}. Then, after an underscore '_'
     * character, a free zone identifier is allowed.
     * <p>
     * Tax zone codes must not be {@code null}, nor empty, and they must not
     * contain any white spaces.
     *
     * @param code
     *            The ISO 3166-1 alpha-2 country code.
     * @throws IllegalArgumentException
     *             when the zone code is not an element of
     *             {@link Locale#getISOCountries()}.
     */
    @JsonCreator
    public TaxZone(String code) throws IllegalArgumentException {
        super();
        checkArgument(StringUtils.isNotBlank(code), "Zone code must not be null, nor empty, nor blank");
        String[] parts = StringUtils.split(code, SEPARATOR);
        checkArgument(parts.length > 0, "Zone code must not be empty");
        String country = parts[0];
        checkArgument(COUNTRIES.get().contains(country), "Illegal country code: [%s]", code);
        if (parts.length > 1) {
            checkArgument(!containsWhitespace(parts[1]), "Must not contain whitespaces");
        }
        this.code = code;
    }

    /**
     * Returns the zone code, whose first part before '_' is an ISO 3166-1
     * alpha-2 country code.
     *
     * @return The code of this zone. Never {@code null}.
     */
    @JsonValue
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
        String country = extractCountryPart(code);
        return new Locale("", country).getDisplayCountry(language);
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
        TaxZone rhs = (TaxZone) obj;
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