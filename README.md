<!--
   Copyright 2015 Benjamin Gandon

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->
Kill Bill Simple Tax Plugin
===========================

This OSGI plugin for the [Kill Bill](http://killbill.io) platform implements
tax codes with  fixed tax rates and cut-off dates. Tax codes can be associated
to products of the Kill Bill catalog, or specifically set on invoice items.

Taxable invoice items then get properly taxed, with the applicable rate, as
specified in tax codes. Regulation-specific rules can be adapted with custom
implementations of the [TaxResolver](https://github.com/bgandon/killbill-simple-tax-plugin/blob/master/src/main/java/org/killbill/billing/plugin/simpletax/resolving/TaxResolver.java)
interface.

The typical use case for this plugin is a regulatory requirement for a bunch
of fixed [VAT](https://en.wikipedia.org/wiki/Value-added_tax) rates that can
change once in a while.


Configuration
-------------

The configuration properties can be specified globally (via System
Properties), or on a per tenant basis. Here is a typical setup for French VAT
rates on the `SpyCarAdvanced.xml` catalog, implementing the cutoff date of
2014-01-01.

```bash
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: text/plain' \
     -d \
'org.killbill.billing.plugin.simpletax.taxResolver = org.killbill.billing.plugin.simpletax.resolving.InvoiceItemEndDateBasedResolver
org.killbill.billing.plugin.simpletax.taxItem.amount.precision = 2

org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_2_1%.taxItem.description = VAT 2.1%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_2_1%.rate = 0.021
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_2_1%.startingOn = 2012-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_2_1%.stoppingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_2_1%.country = FR
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_5_5%.taxItem.description = VAT 5.5%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_5_5%.rate = 0.055
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_5_5%.startingOn = 2012-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_5_5%.stoppingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_5_5%.country = FR
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_10_0%.taxItem.description = VAT 7.0%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_10_0%.rate = 0.070
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_10_0%.startingOn = 2012-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_10_0%.stoppingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_10_0%.country = FR
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_19_6%.taxItem.description = VAT 19.6%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_19_6%.rate = 0.196
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_19_6%.startingOn = 2012-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_19_6%.stoppingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2012_19_6%.country = FR

org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_2_1%.taxItem.description = VAT 2.1%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_2_1%.rate = 0.021
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_2_1%.startingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_2_1%.stoppingOn = 
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_2_1%.country = FR
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_5_5%.taxItem.description = VAT 5.5%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_5_5%.rate = 0.055
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_5_5%.startingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_5_5%.stoppingOn = 
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_5_5%.country = FR
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_10_0%.taxItem.description = VAT 10.0%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_10_0%.rate = 0.100
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_10_0%.startingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_10_0%.stoppingOn = 
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_10_0%.country = FR
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_20_0%.taxItem.description = VAT 20.0%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_20_0%.rate = 0.200
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_20_0%.startingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_20_0%.stoppingOn = 
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_2014_20_0%.country = FR

org.killbill.billing.plugin.simpletax.products.Standard =      VAT_FR_2012_19_6%, VAT_FR_2014_20_0%
org.killbill.billing.plugin.simpletax.products.Sport =         VAT_FR_2012_19_6%, VAT_FR_2014_20_0%
org.killbill.billing.plugin.simpletax.products.Super =         VAT_FR_2012_19_6%, VAT_FR_2014_20_0%
org.killbill.billing.plugin.simpletax.products.OilSlick =      VAT_FR_2012_19_6%, VAT_FR_2014_20_0%
org.killbill.billing.plugin.simpletax.products.RemoteControl = VAT_FR_2012_19_6%, VAT_FR_2014_20_0%
org.killbill.billing.plugin.simpletax.products.Gas =           VAT_FR_2012_19_6%, VAT_FR_2014_20_0%' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-simple-tax
```

The plugin also provides the following REST endpoints to tweak invoice item taxation in more details.

```
GET /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/vatin
PUT /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/vatin
GET /vatins
GET /vatins?account={accountId:\w+-\w+-\w+-\w+-\w+}

GET /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxCountry
PUT /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxCountry
GET /taxCountries
GET /taxCountries?account={accountId:\w+-\w+-\w+-\w+-\w+}
```

The “vatin” endpoints allow assigning [VAT Identification Numbers](https://en.wikipedia.org/wiki/VAT_identification_number)
to accounts. The JSON payload for vatins follows this structure:

```json
{
  "accountId": "<UUID>",
  "vatin": "<VATIN>"
}
```


The “taxCountry” endpoints allow assigning a [two-letter country code](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2)
to an account. Tax codes that are restricted to specific countries will only
apply to accounts that have the same country codes for their tax countries.
(And tax codes not restricted to any country are considered global; they apply
to all accounts.) The JSON payload for tax countries follows this structure:

```json
{
  "accountId": "<UUID>",
  "taxCountry": "<2-Letter-Country-Code>"
}
```


Upcomming REST endpoints:

```
GET /invoices/{invoiceId:\w+-\w+-\w+-\w+-\w+}/taxCodes
POST /invoices/{invoiceId:\w+-\w+-\w+-\w+-\w+}/taxCodes

GET /invoiceItems/{invoiceItemId:\w+-\w+-\w+-\w+-\w+}/taxCodes
PUT /invoiceItems/{invoiceItemId:\w+-\w+-\w+-\w+-\w+}/taxCodes
```

Payload structure for tax codes:

```json
{
  "invoiceItemId": "<UUID>",
  "invoiceId": "<UUID>",
  "taxCodes": [
    {
      "name": "<code-1>"
    },
    {
      "name": "<code-2>"
    },
    ...
  ]
}
```


Upcoming improvements
---------------------

1. Allow editing the tax codes of invoice items
2. Have the precision of tax amounts depend on the currency used
3. Have i18n for tax items descriptions


Building and Installing
-----------------------

Three Maven profiles are provided to help you build the plugin for various
versions of Java.

    mvn -P jdk16 clean package install
    mvn -P jdk17 clean package install
    mvn -P jdk18 clean package install

Then copy the resulting JAR to `/var/tmp/bundles` or any other value set in
the `org.killbill.osgi.bundle.install.dir` system property.

```bash
VERSION=1.0.0-SNAPSHOT
bundles_dir=/var/tmp/bundles # or any other value set in org.killbill.osgi.bundle.install.dir
plugin_dir=$bundles_dir/plugins/java/simple-tax-plugin/$VERSION
mkdir -p $plugin_dir
cp -v ~/.m2/repository/org/kill-bill/billing/plugin/java/simple-tax-plugin/$VERSION/simple-tax-plugin-$VERSION.jar \
    $plugin_dir
```


Author and License
------------------

Copyright © 2015-2016, Benjamin Gandon

As the rest of the Kill Bill platform, this simple tax plugin is released
under the [Apache license](http://www.apache.org/licenses/LICENSE-2.0).
