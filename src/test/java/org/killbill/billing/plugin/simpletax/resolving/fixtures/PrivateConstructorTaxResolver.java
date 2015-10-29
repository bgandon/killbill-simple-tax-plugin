package org.killbill.billing.plugin.simpletax.resolving.fixtures;

import org.killbill.billing.plugin.simpletax.TaxComputationContext;

public class PrivateConstructorTaxResolver extends AbstractTaxResolver {
    private PrivateConstructorTaxResolver(TaxComputationContext ctx) {
        super(ctx);
    }
}