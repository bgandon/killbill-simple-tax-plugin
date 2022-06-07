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

Quick Intro
-----------

This is update version to original `killbill-simple-tax` for Killbill v0.22.x

Version | Killbill | Branch
------|---------|------------------
1.0.0 | v0.18.x | main
1.1.0 | v0.22.x | v0.22.x

---

This OSGI plugin for the [Kill Bill](http://killbill.io) platform implements
tax codes with fixed tax rates and cut-off dates. Tax codes can be associated
to products of the Kill Bill catalog, or specifically set on invoice items.

Tax codes can apply to “the whole world” in a single-country scenario, or they
can be subject to some basic territorial restriction. Currently, they can be
restricted to countries only, and this country must match exactly the tax
country that customers accounts are decorated with.

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
    [...].country = FR

So the “startingOn” and the “stoppingOn” properly model the cutoff dates to
apply with that tax rate of 19.6%.

If you read the properties of `VAT_FR_std_2014_20_0%`, you’ll notice that the
“stoppingOn” cutoff date is not set. That’s because it’s the current rate to
apply, and nobody knows yet until when. When the rate will change, the
“stoppingOn” property will have to be set and a new tax code will have to be
defined withe the same “startingOn” date in order to properly model the new
rate.

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

### Tax Countries

The “country” property of a tax code models a territorial restriction: the tax
codes of the example configuration shall only apply to accounts that have
“taxCountry” properties of “FR”. Any account with no “taxCountry” set or any
“taxCountry” other than “FR” will not be elligible to any of these French tax
codes in their invoices.

So, the current implementeation of territorial restriction has several
limitations.

1. There is no support for retricting tax codes to territories that are
   subdivisions of countries. The only subdivision available is: country.

2. The country of tax codes must match the tax country of customer accounts.
   There is no support for customizing this in case any more complicated rules
   need to be implemented, involving for example a mix of customer location
   and product type. Here the `TaxResolver` interface might help, but it is
   meant to select applicable tax codes taking *time* into account, not the
   geographical *location* or product type.

3. There is no support for customizing how tax code country match the tax
   country of customer accounts. They must match exactly.


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
'org.killbill.billing.plugin.simpletax.taxResolver = org.killbill.billing.plugin.simpletax.resolving.InvoiceItemEndDateBasedResolver
org.killbill.billing.plugin.simpletax.taxItem.amount.precision = 2

# French tax codes (limited set)

org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2000_19_6%.taxItem.description = VAT 19.6%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2000_19_6%.rate = 0.196
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2000_19_6%.startingOn = 2000-04-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2000_19_6%.stoppingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2000_19_6%.country = FR

org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2014_20_0%.taxItem.description = VAT 20%
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2014_20_0%.rate = 0.200
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2014_20_0%.startingOn = 2014-01-01
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2014_20_0%.stoppingOn =
org.killbill.billing.plugin.simpletax.taxCodes.VAT_FR_std_2014_20_0%.country = FR

# Catalog Products

org.killbill.billing.plugin.simpletax.products.Standard =      VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%
org.killbill.billing.plugin.simpletax.products.Sport =         VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%
org.killbill.billing.plugin.simpletax.products.Super =         VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%
org.killbill.billing.plugin.simpletax.products.OilSlick =      VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%
org.killbill.billing.plugin.simpletax.products.RemoteControl = VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%
org.killbill.billing.plugin.simpletax.products.Gas =           VAT_FR_std_2000_19_6%, VAT_FR_std_2014_20_0%

# Credentials
org.killbill.billing.plugin.simpletax.credentials.username = <YOUR CREDENTIALS USERNAME>
org.killbill.billing.plugin.simpletax.credentials.password = <YOUR CREDENTIALS PASSWORD>' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-simple-tax
```
### **Update on v1.1.0:**

The credential properties are required for custom fields manipulation. You can supply Killbill's default credentials, but it's recommended to use [custom credentials](https://docs.killbill.io/latest/user_management.html) designed for this plugin.

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

As a limitation, VATINs can't be deleted yet.


#### Assigning tax countries to accounts

The “taxCountry” endpoints allow assigning a [two-letter country code](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2)
to an account. Tax codes that are restricted to specific countries will only
apply to accounts that have the same country codes for their tax countries.
(And tax codes not restricted to any country are considered global; they apply
to all accounts.)

Method | URI                                                   | OK  | Error Statuses
-------|-------------------------------------------------------|-----|------------------------------------------
GET    | /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxCountry  | 200 | 404: account ID does not exist for tenant
PUT    | /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxCountry  | 201 | 400: when tax country is malformed<br/> 500: when something went wrong while saving value
GET    | /taxCountries                                         | 200 | -
GET    | /taxCountries?account={accountId:\w+-\w+-\w+-\w+-\w+} | 200 | 400: when account ID is malformed

The base JSON payload for tax countries follows this structure:

```json
{
  "accountId": "<UUID>",
  "taxCountry": "<2-Letter-Country-Code>"
}
```

As a limitation, tax countries assignments can't be deleted yet.


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

As a limitation, forced tax codes can't be deleted yet from an invoice item.


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

Copyright © 2015-2017, Benjamin Gandon

As the rest of the Kill Bill platform, this simple tax plugin is released
under the [Apache license](http://www.apache.org/licenses/LICENSE-2.0).
