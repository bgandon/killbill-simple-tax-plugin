package org.killbill.billing.plugin.simpletax.resolving.fixtures;

import java.util.Set;

import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.resolving.TaxResolver;

public abstract class AbstractTaxResolver implements TaxResolver {
    public AbstractTaxResolver(TaxComputationContext ctx) {
    }

    @Override
    public TaxCode applicableCodeForItem(Set<TaxCode> taxCodes, InvoiceItem item) {
        return null;
    }
}