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

import static com.google.common.primitives.Ints.tryParse;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.substring;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.killbill.billing.plugin.simpletax.util.ConcurrentLazyValue;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * A validator for VAT Identification Numbers (VATIN), based on the <a
 * href="http://www.braemoor.co.uk/software/vat.shtml">JavaScript EU VAT Number
 * Validation</a>.
 *
 * @author Benjamin Gandon
 */
public class VATINValidator implements Predicate<String> {

    private static final int COUNTRY_PREFIX_LENGTH = 2;
    private static final int VATIN_TAIL_GROUP = 2;

    private static Pair<Pattern, Predicate<String>> pair(Pattern pattern, Predicate<String> validator) {
        return ImmutablePair.of(pattern, validator);
    }

    private static final Supplier<Multimap<String, Pair<Pattern, Predicate<String>>>> PATTERNS = new ConcurrentLazyValue<Multimap<String, Pair<Pattern, Predicate<String>>>>() {
        @Override
        protected Multimap<String, Pair<Pattern, Predicate<String>>> initialize() {
            return ImmutableMultimap.<String, Pair<Pattern, Predicate<String>>> builder()//

                    // Austria
                    .put("AT", pair(compile("(AT)U(\\d{8})"), null))//
                    .put("BE", pair(compile("(BE)(0?\\d{9})"), null))// Belgium
                    .put("BG", pair(compile("(BG)(\\d{9,10})"), null))// Bulgaria
                    .put("CH", pair(compile("(CHE)(\\d{9})(MWST)?"), null))// Switzerland
                    .put("CY", pair(compile("(CY)([0-59]\\d{7}[A-Z])"), null))// Cyprus

                    // Czech Republic
                    .put("CZ", pair(compile("(CZ)(\\d{8,10})(\\d{3})?"), null))//
                    .put("DE", pair(compile("(DE)([1-9]\\d{8})"), null))// Germany
                    .put("DK", pair(compile("(DK)(\\d{8})"), null))// Denmark
                    .put("EE", pair(compile("(EE)(10\\d{7})"), null))// Estonia
                    .put("EL", pair(compile("(EL)(\\d{9})"), null))// Greece

                    // Spain (National juridical entities)
                    .put("ES", pair(compile("(ES)([A-Z]\\d{8})"), null))//
                    // Spain (Other juridical entities)
                    .put("ES", pair(compile("(ES)([A-HN-SW]\\d{7}[A-J])"), null))//
                    // Spain (Personal entities type 1)
                    .put("ES", pair(compile("(ES)([0-9YZ]\\d{7}[A-Z])"), null))//
                    // Spain (Personal entities type 2)
                    .put("ES", pair(compile("(ES)([KLMX]\\d{7}[A-Z])"), null))//

                    .put("EU", pair(compile("(EU)(\\d{9})"), null))// EU-type
                    .put("FI", pair(compile("(FI)(\\d{8})"), null))// Finland

                    // France (1)
                    .put("FR", pair(compile("(FR)(\\d{11})"), new FRVATVal()))//
                    // France (2)
                    .put("FR", pair(compile("(FR)([A-HJ-NP-Z]\\d{10})"), null))//
                    // France (3)
                    .put("FR", pair(compile("(FR)(\\d[A-HJ-NP-Z]\\d{9})"), null))//
                    // France (4)
                    .put("FR", pair(compile("(FR)([A-HJ-NP-Z]{2}\\d{9})"), null))//

                    // UK (Standard)
                    .put("GB", pair(compile("(GB)?(\\d{9})"), null))//
                    // UK (Branches)
                    .put("GB", pair(compile("(GB)?(\\d{12})"), null))//
                    // UK (Government)
                    .put("GB", pair(compile("(GB)?(GD\\d{3})"), null))//
                    // UK (Health authority)
                    .put("GB", pair(compile("(GB)?(HA\\d{3})"), null))//

                    .put("HR", pair(compile("(HR)(\\d{11})"), null))// Croatia
                    .put("HU", pair(compile("(HU)(\\d{8})"), null))// Hungary

                    // Ireland (1)
                    .put("IE", pair(compile("(IE)(\\d{7}[A-W])"), null))//
                    // Ireland (2)
                    .put("IE", pair(compile("(IE)([7-9][A-Z\\*\\+)]\\d{5}[A-W])"), null))//
                    // Ireland (3)
                    .put("IE", pair(compile("(IE)(\\d{7}[A-W][AH])"), null))//

                    .put("IT", pair(compile("(IT)(\\d{11})"), null))// Italy
                    .put("LV", pair(compile("(LV)(\\d{11})"), null))// Latvia
                    .put("LT", pair(compile("(LT)(\\d{9}|\\d{12})"), null))// Lithunia
                    .put("LU", pair(compile("(LU)(\\d{8})"), null))// Luxembourg
                    .put("MT", pair(compile("(MT)([1-9]\\d{7})"), null))// Malta
                    .put("NL", pair(compile("(NL)(\\d{9})B\\d{2}"), null))// Netherlands
                    .put("NO", pair(compile("(NO)(\\d{9})"), null))// Norway
                    // (not EU)
                    .put("PL", pair(compile("(PL)(\\d{10})"), null))// Poland
                    .put("PT", pair(compile("(PT)(\\d{9})"), null))// Portugal
                    .put("RO", pair(compile("(RO)([1-9]\\d{1,9})"), null))// Romania
                    .put("RU", pair(compile("(RU)(\\d{10}|\\d{12})"), null))// Russia
                    .put("RS", pair(compile("(RS)(\\d{9})"), null))// Serbia
                    .put("SI", pair(compile("(SI)([1-9]\\d{7})"), null))// Slovenia

                    // Slovakia Republic
                    .put("SK", pair(compile("(SK)([1-9]\\d[2346-9]\\d{7})"), null))//
                    .put("SE", pair(compile("(SE)(\\d{10}01)"), null))// Sweden
                    .build();
        }
    };

    @Override
    public boolean apply(String vatin) {
        String countryPrefix = substring(vatin, 0, COUNTRY_PREFIX_LENGTH);
        for (Pair<Pattern, Predicate<String>> candidate : PATTERNS.get().get(countryPrefix)) {
            Matcher matcher = candidate.getLeft().matcher(vatin);
            if (matcher.matches()) {
                String vatinTail = matcher.group(VATIN_TAIL_GROUP);
                return candidate.getRight().apply(vatinTail);
            }
        }
        return false;
    }

    private static class FRVATVal implements Predicate<String> {
        private static final Pattern ALL_NUMERIC = compile("\\d{11}");
        private static final int PFX_LEN = 2;

        @Override
        public boolean apply(String vatinTail) {
            if (!ALL_NUMERIC.matcher(vatinTail).matches()) {
                // We don't know how to validate alternate forms (2), (3) and
                // (4) with letters
                return true;
            }
            long num = tryParse(vatinTail.substring(PFX_LEN));
            // Note: 'num' cannot be null here because we checked 'vatinTail'
            // for being all numeric above
            long hash = ((num * 100L) + 12L) % 97L;
            long checksum = tryParse(vatinTail.substring(0, PFX_LEN));
            return hash == checksum;
        }
    }
}
