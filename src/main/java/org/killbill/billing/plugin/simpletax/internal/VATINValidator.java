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

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.killbill.billing.plugin.simpletax.util.ConcurrentLazyValue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.primitives.Ints.tryParse;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.substring;

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
                    .put("AT", pair(compile("(AT)U(\\d{8})"), ALWAYS_OK))//
                    .put("BE", pair(compile("(BE)(0?\\d{9})"), ALWAYS_OK))// Belgium
                    .put("BG", pair(compile("(BG)(\\d{9,10})"), ALWAYS_OK))// Bulgaria
                    .put("CH", pair(compile("(CHE)(\\d{9})(MWST)?"), ALWAYS_OK))// Switzerland
                    .put("CY", pair(compile("(CY)([0-59]\\d{7}[A-Z])"), ALWAYS_OK))// Cyprus

                    // Czech Republic
                    .put("CZ", pair(compile("(CZ)(\\d{8,10})(\\d{3})?"), ALWAYS_OK))//
                    .put("DE", pair(compile("(DE)([1-9]\\d{8})"), ALWAYS_OK))// Germany
                    .put("DK", pair(compile("(DK)(\\d{8})"), ALWAYS_OK))// Denmark
                    .put("EE", pair(compile("(EE)(10\\d{7})"), ALWAYS_OK))// Estonia
                    .put("EL", pair(compile("(EL)(\\d{9})"), ALWAYS_OK))// Greece

                    // Spain (National juridical entities)
                    .put("ES", pair(compile("(ES)([A-Z]\\d{8})"), ALWAYS_OK))//
                    // Spain (Other juridical entities)
                    .put("ES", pair(compile("(ES)([A-HN-SW]\\d{7}[A-J])"), ALWAYS_OK))//
                    // Spain (Personal entities type 1)
                    .put("ES", pair(compile("(ES)([0-9YZ]\\d{7}[A-Z])"), ALWAYS_OK))//
                    // Spain (Personal entities type 2)
                    .put("ES", pair(compile("(ES)([KLMX]\\d{7}[A-Z])"), ALWAYS_OK))//

                    .put("EU", pair(compile("(EU)(\\d{9})"), ALWAYS_OK))// EU-type
                    .put("FI", pair(compile("(FI)(\\d{8})"), ALWAYS_OK))// Finland

                    // France (1)
                    .put("FR", pair(compile("(FR)(\\d{11})"), new FRVATVal()))//
                    // France (2)
                    .put("FR", pair(compile("(FR)([A-HJ-NP-Z]\\d{10})"), ALWAYS_OK))//
                    // France (3)
                    .put("FR", pair(compile("(FR)(\\d[A-HJ-NP-Z]\\d{9})"), ALWAYS_OK))//
                    // France (4)
                    .put("FR", pair(compile("(FR)([A-HJ-NP-Z]{2}\\d{9})"), ALWAYS_OK))//

                    // UK (Standard)
                    .put("GB", pair(compile("(GB)?(\\d{9})"), ALWAYS_OK))//
                    // UK (Branches)
                    .put("GB", pair(compile("(GB)?(\\d{12})"), ALWAYS_OK))//
                    // UK (Government)
                    .put("GB", pair(compile("(GB)?(GD\\d{3})"), ALWAYS_OK))//
                    // UK (Health authority)
                    .put("GB", pair(compile("(GB)?(HA\\d{3})"), ALWAYS_OK))//

                    .put("HR", pair(compile("(HR)(\\d{11})"), ALWAYS_OK))// Croatia
                    .put("HU", pair(compile("(HU)(\\d{8})"), ALWAYS_OK))// Hungary

                    // Ireland (1)
                    .put("IE", pair(compile("(IE)(\\d{7}[A-W])"), ALWAYS_OK))//
                    // Ireland (2)
                    .put("IE", pair(compile("(IE)([7-9][A-Z\\*\\+)]\\d{5}[A-W])"), ALWAYS_OK))//
                    // Ireland (3)
                    .put("IE", pair(compile("(IE)(\\d{7}[A-W][AH])"), ALWAYS_OK))//

                    .put("IT", pair(compile("(IT)(\\d{11})"), ALWAYS_OK))// Italy
                    .put("LV", pair(compile("(LV)(\\d{11})"), ALWAYS_OK))// Latvia
                    .put("LT", pair(compile("(LT)(\\d{9}|\\d{12})"), ALWAYS_OK))// Lithunia
                    .put("LU", pair(compile("(LU)(\\d{8})"), ALWAYS_OK))// Luxembourg
                    .put("MT", pair(compile("(MT)([1-9]\\d{7})"), ALWAYS_OK))// Malta
                    .put("NL", pair(compile("(NL)(\\d{9})B\\d{2}"), ALWAYS_OK))// Netherlands
                    .put("NO", pair(compile("(NO)(\\d{9})"), ALWAYS_OK))// Norway
                    // (not EU)
                    .put("PL", pair(compile("(PL)(\\d{10})"), ALWAYS_OK))// Poland
                    .put("PT", pair(compile("(PT)(\\d{9})"), ALWAYS_OK))// Portugal
                    .put("RO", pair(compile("(RO)([1-9]\\d{1,9})"), ALWAYS_OK))// Romania
                    .put("RU", pair(compile("(RU)(\\d{10}|\\d{12})"), ALWAYS_OK))// Russia
                    .put("RS", pair(compile("(RS)(\\d{9})"), ALWAYS_OK))// Serbia
                    .put("SI", pair(compile("(SI)([1-9]\\d{7})"), ALWAYS_OK))// Slovenia

                    // Slovakia Republic
                    .put("SK", pair(compile("(SK)([1-9]\\d[2346-9]\\d{7})"), ALWAYS_OK))//
                    .put("SE", pair(compile("(SE)(\\d{10}01)"), ALWAYS_OK))// Sweden
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

    private static Predicate<String> ALWAYS_OK = new AlwaysPass();

    private static class AlwaysPass implements Predicate<String> {
        @Override
        public boolean apply(String input) {
            return true;
        }
    }

    private static class FRVATVal implements Predicate<String> {
        private static final int PFX_LEN = 2;

        @Override
        public boolean apply(String vatinTail) {
            long num = tryParse(vatinTail.substring(PFX_LEN));
            // Note: 'num' cannot be null here because we checked 'vatinTail'
            // for being all numeric above
            long hash = ((num * 100L) + 12L) % 97L;
            long checksum = tryParse(vatinTail.substring(0, PFX_LEN));
            return hash == checksum;
        }
    }
}
