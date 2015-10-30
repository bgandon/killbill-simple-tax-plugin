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
package countries;

import static java.lang.System.out;
import static java.util.Locale.ENGLISH;
import static java.util.Locale.FRENCH;
import static java.util.Locale.getAvailableLocales;
import static java.util.Locale.getISOCountries;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Inspired by <a
 * href="http://www.mkyong.com/java/display-a-list-of-countries-in-java/"
 * >Display a list of countries in Java</a>.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class ListCountry {

    private Map<String, String> languagesOfCountries = new TreeMap<String, String>();

    public ListCountry() {
        initLanguageMap();
    }

    public static void main(String[] args) {
        new ListCountry().getListOfCountries();
    }

    private static String parens(String text) {
        return new StringBuilder().append('(').append(text).append(')').toString();
    }

    public void getListOfCountries() {
        int supportedLocale = 0, nonSupportedLocale = 0;

        for (String countryCode : getISOCountries()) {

            Locale locale = null;
            if (!languagesOfCountries.containsKey(countryCode)) {
                locale = new Locale("", countryCode);
                nonSupportedLocale++;
            } else {
                // create a Locale with own country's languages
                locale = new Locale(languagesOfCountries.get(countryCode), countryCode);
                supportedLocale++;
            }
            out.printf("Country Code: %1$2s, Name: %2$-45s %3$-45s %4$-45s Languages: %5$10s\n", locale.getCountry(),
                    locale.getDisplayCountry(ENGLISH), parens(locale.getDisplayCountry(FRENCH)),
                    parens(locale.getDisplayCountry(locale)), locale.getDisplayLanguage());
        }
        out.println("nonSupportedLocale: " + nonSupportedLocale);
        out.println("supportedLocale: " + supportedLocale);
    }

    /** Create Map with country code and languages. */
    public void initLanguageMap() {

        for (Locale locale : getAvailableLocales()) {
            if (!isBlank(locale.getDisplayCountry())) {
                languagesOfCountries.put(locale.getCountry(), locale.getLanguage());
            }
        }
    }
}