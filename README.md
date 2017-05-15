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
Kill Bill Simple Tax Plugin [![.](http://gaproxy.gstack.io/UA-68445280-1/bgandon/killbill-simple-tax-plugin/readme?pixel&dh=github.com)](https://github.com/gstackio/ga-beacon)
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


How it works
------------

In this section, the explanations refer to the the example configuration
below. You’ll need to take a look at it in order to understand how the plugin
works.

### Cutoff Dates

If you take the example of a “Standard” car in the
[“SpyCarAdvanced” example catalog](http://docs.killbill.io/0.16/userguide_subscription.html#components-catalog-advanced),
then the car rental service is subject to a 20% VAT rate in France. But this
is only valid after 2014-01-01. Before that, from 2012-01-01 (included) to
2014-01-01 (excluded) it was a 19.6% VAT rate. (And before 2012-01-01, VAT was
20% but we don’t mind here. Let’s just say we need to deal with taxation for
services that started being sold in 2013.)

To deal with that cutoff date (which is well known in France, but everybody
around the world is not supposed to know, sorry for that), the example config
sets up 2 tax codes: `VAT_FR_std_2000_19_6%` and then `VAT_FR_std_2014_20_0%`
(Please note that the percent sign is just a valid character for a tax code
label; it’s just plain text with no special meaning.)

If you dig into the details of the first one, you’ll see these properties:

    [...].description = VAT 19.6%
    [...].rate = 0.196
    [...].startingOn = 2012-01-01
    [...].stoppingOn = 2014-01-01
    [...].taxZone = FR

So the “startingOn” and the “stoppingOn” properly model the cutoff dates to
apply with that tax rate of 19.6%.

If you read the properties of `VAT_FR_std_2014_20_0%`, you’ll notice that the
“stoppingOn” cutoff date is not set. That’s because it’s the current rate to
apply, and nobody knows yet until when. When the rate will change, the
“stoppingOn” property shall be set and a new tax code shall be defined with
the same “startingOn” date in order to properly model the new rate.

This comes down to a very important principle in the simple-tax plugin:
**configured tax codes should always be considered immutables, except their
“stoppingOn” properties** which are the only one that might change over time.

Now imagine that our company has charged a car rental from 2013-12-01 until
2014-01-31 included. Which tax code should apply? Here the `TaxResolver` comes
into play. Currently it just says: the end date should prevail. Here the end
date is in 2014, so the new tax rate of 20% will apply.

Had we charged a “Standard” car rental from 2013-12-01 until 2013-12-31, then
the `TaxResolver` resolution would have led to a 19.6% VAT rate because
2013-12-31 is in 2013.

### Tax Zones

The “taxZone” property of a tax code models a territorial restriction: the tax
codes of the example configuration shall only apply to accounts that have
“taxZone” properties of “FR”. Any account with no “taxZone” set or any
“taxZone” other than “FR” will not be elligible to any of these French tax
codes in their invoices.


Configuration
-------------

### Configuring the plugin

The configuration properties can be specified globally (via System
Properties), or on a per-tenant basis. Here is an example setup for French VAT
rates on the `SpyCarAdvanced.xml` catalog, implementing the cutoff date of
2014-01-01. You can find a much more detailed example configuration in
[eu-vat-example-config.properties](./src/main/resources/eu-vat-example-config.properties).

```bash
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: text/plain' \
     -d \
'org.killbill.billing.plugin.simpletax.merchantTaxZone = FR
org.killbill.billing.plugin.simpletax.taxResolver = org.killbill.billing.plugin.simpletax.resolving.InvoiceItemEndDateBasedResolver
org.killbill.billing.plugin.simpletax.taxItem.amount.precision = 2

# French tax codes (limited set)

org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2000_19_6%.taxItem.description = VAT 19.6%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2000_19_6%.rate = 0.196
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2000_19_6%.startingOn = 2000-04-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2000_19_6%.stoppingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2000_19_6%.zone = FR

org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2014_20_0%.taxItem.description = VAT 20%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2014_20_0%.rate = 0.200
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2014_20_0%.startingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2014_20_0%.stoppingOn =
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2014_20_0%.zone = FR

# Catalog Products

org.killbill.billing.plugin.simpletax.products.Standard =      VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%
org.killbill.billing.plugin.simpletax.products.Sport =         VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%
org.killbill.billing.plugin.simpletax.products.Super =         VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%
org.killbill.billing.plugin.simpletax.products.OilSlick =      VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%
org.killbill.billing.plugin.simpletax.products.RemoteControl = VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%
org.killbill.billing.plugin.simpletax.products.Gas =           VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-simple-tax
```


### Configuring accounts

The plugin also provides the following REST endpoints to tweak taxation at the
account level.

#### Assigning VAT Identification Numbers to accounts

The “vatin” endpoints allow assigning [VAT Identification Numbers](https://en.wikipedia.org/wiki/VAT_identification_number)
to accounts.

Method | URI                                             | OK  | Error Statuses
-------|-------------------------------------------------|-----|-------------------------------------------
GET    | /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/vatin | 200 | 404: account ID does not exist for tenant
PUT    | /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/vatin | 201 | 400: when VATIN is malformed _for sure_ (when VATIN cannot be validated, it is stored as-is) <br/> 500: when something went wrong while saving value
GET    | /vatins                                         | 200 | -
GET    | /vatins?account={accountId:\w+-\w+-\w+-\w+-\w+} | 200 | 400: when account ID is malformed

The base JSON payload for VATINs follows this structure:

```json
{
  "accountId": "<UUID>",
  "vatin": "<VATIN>"
}
```


#### Assigning Tax Zones to accounts

The “taxZone” endpoints allow assigning as administrative zone (usually a
country) in which a customer account is declaring and paying its taxes.

A tax zone is composed of two parts separated by an underscore `_` character.
1. A mandatory [two-letter country code](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2)
2. An optional tax zone refinement identifier string with no spaces.

Examples: `FR` (metropolitan France excluding Corsica), and `FR_CORSICA` (only
Corsica)

A Tax Code that is restricted to a specific Tax Zone will only apply to
accounts that have the exact same Tax Zone. (And tax codes not restricted to
any zone are considered global—they apply to all accounts.)

Method | URI                                               | OK  | Error Statuses
-------|---------------------------------------------------|-----|------------------------------------------
GET    | /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxZone | 200 | 404: account ID does not exist for tenant
PUT    | /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxZone | 201 | 400: when tax zone is malformed<br/> 500: when something went wrong while saving value
GET    | /taxZones                                         | 200 | -
GET    | /taxZones?account={accountId:\w+-\w+-\w+-\w+-\w+} | 200 | 400: when account ID is malformed

The base JSON payload for tax zones follows this structure:

```json
{
  "accountId": "<UUID>",
  "taxZone": "<2-Letter-Country-Code>_<any string without any spaces>"
}
```

### Forcing specific tax codes on existing invoice items

For existing invoices, the plugin provides REST endpoints that allow tweaking
the tax codes that have been set (or not) to their items.

After changing a tax code of an invoice item, you'll need to re-run the
invoice generation process. New tax items or adjustment items will be created
accordingly to properly match the newly declared taxes.

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

1. Implement critical user stories for European VAT:
   - B2B that have a valid VAT number aren’t charged VAT
   - B2B that don’t have a VAT number are charged VAT at their local rate
2. Build a more comprehensive example configuration that embraces more
   European countries
3. Have the precision of tax amounts depend on the currency used
4. Have i18n for tax items descriptions


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
