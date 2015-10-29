package org.killbill.billing.plugin.simpletax.resolving.fixtures;

import org.killbill.billing.plugin.simpletax.TaxComputationContext;

public class ThrowingTaxResolver extends AbstractTaxResolver {
    public ThrowingTaxResolver(TaxComputationContext ctx) {
        super(ctx);
        throw new RuntimeException();
    }
}