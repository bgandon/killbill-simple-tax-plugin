Kill Bill Simple Tax Plugin
===========================

This OSGI plugin for the [Kill Bill](http://killbill.io) platform implements a
fixed tax rate that applies to all taxable items of generated invoices.

The typical use case for this plugin is a regulatory requirement for a fixed
[VAT](https://en.wikipedia.org/wiki/Value-added_tax) rate.


Configuration
-------------

The configuration properties can be specified globally (via System
Properties), or on a per tenant basis:

```bash
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: text/plain' \
     -d \
'org.killbill.billing.plugin.simpletax.tax-item.description=Tax
org.killbill.billing.plugin.simpletax.tax-amount.precision=2
org.killbill.billing.plugin.simpletax.tax-rate=0.20
org.killbill.billing.plugin.simpletax.tax-rate.precision=2' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-simple-tax
```

The values above are the default values.


Upcoming improvements
---------------------

1. Implement single-tenant config
2. Implement multi-tenant config
3. Implement cutoff dates for planned tax-rate changes


Author and License
------------------

Copyright © 2015, Benjamin Gandon

As the rest of the Kill Bill platform, this simple tax plugin is released
under the [Apache license](http://www.apache.org/licenses/LICENSE-2.0).
