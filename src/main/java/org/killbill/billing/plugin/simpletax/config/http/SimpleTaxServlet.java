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
package org.killbill.billing.plugin.simpletax.config.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.killbill.billing.plugin.core.PluginServlet;
import org.killbill.billing.plugin.simpletax.config.http.TaxCodeController.TaxCodesPOSTRsc;
import org.killbill.billing.plugin.simpletax.config.http.TaxCodeController.TaxCodesPUTRsc;
import org.killbill.billing.plugin.simpletax.config.http.TaxCountryController.TaxCountryRsc;
import org.killbill.billing.plugin.simpletax.config.http.VatinController.VATINRsc;
import org.killbill.billing.tenant.api.Tenant;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.UUID_LOOSE_PATTERN;
import static org.killbill.billing.plugin.simpletax.config.ConvertionHelpers.toUUIDOrNull;
import static org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxActivator.PLUGIN_NAME;

/**
 * A {@link PluginServlet} that provides endpoints to setup and review the
 * accounts custom properties that are necessary to the simple tax plugin.
 * <p>
 * <strong>Summary of methods:</strong>
 *
 * <pre>
 * GET /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/vatin
 * PUT /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/vatin
 * GET /vatins
 * GET /vatins?account={accountId:\w+-\w+-\w+-\w+-\w+}
 *
 * GET /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxCountry
 * PUT /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxCountry
 * GET /taxCountries
 * GET /taxCountries?account={accountId:\w+-\w+-\w+-\w+-\w+}
 *
 * GET /invoices/{invoiceId:\w+-\w+-\w+-\w+-\w+}/taxCodes
 * POST /invoices/{invoiceId:\w+-\w+-\w+-\w+-\w+}/taxCodes
 *
 * GET /invoiceItems/{invoiceItemId:\w+-\w+-\w+-\w+-\w+}/taxCodes
 * PUT /invoiceItems/{invoiceItemId:\w+-\w+-\w+-\w+-\w+}/taxCodes
 * </pre>
 * <p>
 * We don't use the standard <code>/accounts/{accountId}/customFields</code>
 * endpoint here because they don't enforce any data validation.
 *
 * <pre>
 * GET /1.0/kb/accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/customFields
 * POST /1.0/kb/accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/customFields
 * {
 *     "customFieldId": "java.util.UUID",
 *     "objectId": "java.util.UUID",
 *     "objectType": "ObjectType",
 *     "name": "",
 *     "value": ""
 * }
 * </pre>
 *
 * @author Benjamin Gandon
 */
public class SimpleTaxServlet extends PluginServlet {
    private static final long serialVersionUID = 1L;

    private static final String PLUGIN_BASE_PATH = "/plugins/" + PLUGIN_NAME;

    private static final String ACCOUNTS_PATH = "/accounts";
    private static final Pattern ACCOUNT_PATTERN = compile(ACCOUNTS_PATH + "/(" + UUID_LOOSE_PATTERN + ")/(\\w+)");
    private static final int RESOURCE_IDENTIFIER_GROUP = 1;
    private static final int RESOURCE_NAME_GROUP = 2;
    private static final String VATIN_RESOURCE_NAME = "vatin";
    private static final String TAX_COUNTRY_RESOURCE_NAME = "taxCountry";

    private static final String TAX_COUNTRIES_PATH = "/taxCountries";
    private static final String VATINS_PATH = "/vatins";
    private static final String ACCOUNT_PARAM_NAME = "account";
    private static final Pattern LOOSE_UUID = compile(UUID_LOOSE_PATTERN);

    private static final String INVOICES_PATH = "/invoices";
    private static final Pattern INVOICE_PATTERN = compile(INVOICES_PATH + "/(" + UUID_LOOSE_PATTERN + ")/(\\w+)");
    private static final String INVOICE_ITEMS_PATH = "/invoiceItems";
    private static final Pattern INVOICE_ITEM_PATTERN = compile(INVOICE_ITEMS_PATH + "/(" + UUID_LOOSE_PATTERN
            + ")/(\\w+)");
    private static final String TAX_CODES_RESOURCE_NAME = "taxCodes";

    private static String accountResourceUri(UUID accountId, String resourceName) {
        return resourceUri(ACCOUNTS_PATH, accountId, resourceName).toString();
    }

    private static String invoiceResourceUri(UUID invoiceId, String resourceName) {
        return resourceUri(INVOICES_PATH, invoiceId, resourceName).toString();
    }

    private static String invoiceItemResourceUri(UUID invoiceItemId, String resourceName) {
        return resourceUri(INVOICE_ITEMS_PATH, invoiceItemId, resourceName).toString();
    }

    private static StringBuilder resourceUri(String resourceName, UUID identifier, String subResourceName) {
        return new StringBuilder(PLUGIN_BASE_PATH).append(resourceName).append('/').append(identifier).append('/')
                .append(subResourceName);
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private TaxCountryController taxCountryController;
    private VatinController vatinController;
    private TaxCodeController taxCodeController;

    /**
     * Constructs a new servlet for configuring data related to the simple tax
     * plugin.
     *
     * @param vatinController
     *            The VATIN controller to use.
     * @param taxCountryController
     *            The tax country controller to use.
     * @param taxCodeController
     *            The tax code controller to use.
     */
    public SimpleTaxServlet(VatinController vatinController, TaxCountryController taxCountryController,
            TaxCodeController taxCodeController) {
        super();
        this.taxCountryController = taxCountryController;
        this.vatinController = vatinController;
        this.taxCodeController = taxCodeController;
    }

    /**
     * This implementation serves these HTTP end points:
     *
     * <pre>
     * GET /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxCountry
     * GET /taxCountries
     * GET /taxCountries?account={accountId:\w+-\w+-\w+-\w+-\w+}
     *
     * GET /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/vatin
     * GET /vatins
     * GET /vatins?account={accountId:\w+-\w+-\w+-\w+-\w+}
     *
     * GET /invoices/{invoiceId:\w+-\w+-\w+-\w+-\w+}/taxCodes
     * </pre>
     *
     * Other endpoints could be provided in the future, when the Kill Bill API
     * make them reasonable to provide:
     *
     * <pre>
     * GET /invoiceItems/{invoiceItemId:\w+-\w+-\w+-\w+-\w+}/taxCodes
     * </pre>
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Tenant tenant = getTenant(req);
        if (tenant == null) {
            buildNotFoundResponse("No tenant specified by the 'X-Killbill-ApiKey'"
                    + " and 'X-Killbill-ApiSecret' headers", resp);
            return;
        }
        String pathInfo = req.getPathInfo();
        Matcher matcher = ACCOUNT_PATTERN.matcher(pathInfo);
        if (matcher.matches()) {
            UUID accountId = toUUIDOrNull(matcher.group(RESOURCE_IDENTIFIER_GROUP));
            if (accountId == null) {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
            String resourceName = matcher.group(RESOURCE_NAME_GROUP);
            if (TAX_COUNTRY_RESOURCE_NAME.equals(resourceName)) {
                Object value = taxCountryController.getAccountTaxCountry(accountId, tenant);
                writeJsonOkResponse(value, resp);
                return;
            } else if (VATIN_RESOURCE_NAME.equals(resourceName)) {
                Object value = vatinController.getAccountVatin(accountId, tenant);
                writeJsonOkResponse(value, resp);
                return;
            } else {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
        }

        if (TAX_COUNTRIES_PATH.equals(pathInfo)) {
            String account = req.getParameter(ACCOUNT_PARAM_NAME);
            if (isBlank(account)) {
                Object value = taxCountryController.listTaxCountries(null, tenant);
                writeJsonOkResponse(value, resp);
                return;
            }
            UUID accountId = null;
            if (LOOSE_UUID.matcher(account).matches()) {
                accountId = toUUIDOrNull(account);
            }
            if (accountId == null) {
                resp.sendError(SC_BAD_REQUEST, "Illegal value [" + account + "] for request parameter ["
                        + ACCOUNT_PARAM_NAME + "]");
                return;
            }
            Object value = taxCountryController.listTaxCountries(accountId, tenant);
            writeJsonOkResponse(value, resp);
            return;
        }
        if (VATINS_PATH.equals(pathInfo)) {
            String account = req.getParameter(ACCOUNT_PARAM_NAME);
            if (isBlank(account)) {
                Object value = vatinController.listVatins(null, tenant);
                writeJsonOkResponse(value, resp);
                return;
            }
            UUID accountId = null;
            if (LOOSE_UUID.matcher(account).matches()) {
                accountId = toUUIDOrNull(account);
            }
            if (accountId == null) {
                resp.sendError(SC_BAD_REQUEST, "Illegal value [" + account + "] for request parameter ["
                        + ACCOUNT_PARAM_NAME + "]");
                return;
            }
            Object value = vatinController.listVatins(accountId, tenant);
            writeJsonOkResponse(value, resp);
            return;
        }

        matcher = INVOICE_PATTERN.matcher(pathInfo);
        if (matcher.matches()) {
            UUID invoiceId = toUUIDOrNull(matcher.group(RESOURCE_IDENTIFIER_GROUP));
            if (invoiceId == null) {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
            String resourceName = matcher.group(RESOURCE_NAME_GROUP);
            if (!TAX_CODES_RESOURCE_NAME.equals(resourceName)) {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
            Object value = taxCodeController.listInvoiceTaxCodes(invoiceId, tenant);
            writeJsonOkResponse(value, resp);
            return;
        }

        matcher = INVOICE_ITEM_PATTERN.matcher(pathInfo);
        if (matcher.matches()) {
            UUID invoiceItemId = toUUIDOrNull(matcher.group(RESOURCE_IDENTIFIER_GROUP));
            if (invoiceItemId == null) {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
            String resourceName = matcher.group(RESOURCE_NAME_GROUP);
            if (!TAX_CODES_RESOURCE_NAME.equals(resourceName)) {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
            Object value = taxCodeController.getTaxCodesOfInvoiceItem(invoiceItemId, tenant);
            writeJsonOkResponse(value, resp);
        }

        buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
    }

    /**
     * This implementation serves this HTTP end point:
     *
     * <pre>
     * POST /invoices/{invoiceId:\w+-\w+-\w+-\w+-\w+}/taxCodes
     * </pre>
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Tenant tenant = getTenant(req);
        if (tenant == null) {
            buildNotFoundResponse("No tenant specified by the 'X-Killbill-ApiKey'"
                    + " and 'X-Killbill-ApiSecret' headers", resp);
            return;
        }
        String pathInfo = req.getPathInfo();

        Matcher matcher = INVOICE_PATTERN.matcher(pathInfo);
        if (matcher.matches()) {
            UUID invoiceId = toUUIDOrNull(matcher.group(RESOURCE_IDENTIFIER_GROUP));
            if (invoiceId == null) {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
            String resourceName = matcher.group(RESOURCE_NAME_GROUP);
            if (!TAX_CODES_RESOURCE_NAME.equals(resourceName)) {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
            TaxCodesPOSTRsc taxCodesRsc;
            try {
                taxCodesRsc = JSON_MAPPER.readValue(getRequestData(req), TaxCodesPOSTRsc.class);
            } catch (JsonProcessingException exc) {
                taxCodesRsc = null;
            }
            if (taxCodesRsc == null) {
                resp.sendError(SC_BAD_REQUEST, "Invalid Tax Codes resource in request body");
                return;
            }
            boolean saved = taxCodeController.saveInvoiceTaxCodes(invoiceId, taxCodesRsc, tenant);
            if (!saved) {
                resp.sendError(SC_INTERNAL_SERVER_ERROR, "Could not save Tax Codes resource");
                return;
            }
            buildCreatedResponse(invoiceResourceUri(invoiceId, TAX_CODES_RESOURCE_NAME), resp);
            return;
        }

        buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
    }

    /**
     * This implementation serves these HTTP end points:
     *
     * <pre>
     * PUT /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/vatin
     * 
     * PUT /accounts/{accountId:\w+-\w+-\w+-\w+-\w+}/taxCountry
     * 
     * PUT /invoiceItems/{invoiceItemId:\w+-\w+-\w+-\w+-\w+}/taxCodes
     * </pre>
     */
    @Override
    public void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Tenant tenant = getTenant(req);
        if (tenant == null) {
            buildNotFoundResponse("No tenant specified by the 'X-Killbill-ApiKey'"
                    + " and 'X-Killbill-ApiSecret' headers", resp);
            return;
        }
        String pathInfo = req.getPathInfo();

        Matcher matcher = ACCOUNT_PATTERN.matcher(pathInfo);
        if (matcher.matches()) {
            UUID accountId = toUUIDOrNull(matcher.group(RESOURCE_IDENTIFIER_GROUP));
            if (accountId == null) {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
            String resourceName = matcher.group(RESOURCE_NAME_GROUP);
            if (TAX_COUNTRY_RESOURCE_NAME.equals(resourceName)) {
                TaxCountryRsc taxCountry;
                try {
                    taxCountry = JSON_MAPPER.readValue(getRequestData(req), TaxCountryRsc.class);
                } catch (JsonProcessingException exc) {
                    taxCountry = null;
                }
                if (taxCountry == null) {
                    resp.sendError(SC_BAD_REQUEST, "Invalid tax country resource in request body");
                    return;
                }
                boolean saved = taxCountryController.saveAccountTaxCountry(accountId, taxCountry, tenant);
                if (!saved) {
                    resp.sendError(SC_INTERNAL_SERVER_ERROR, "Could not save tax country resource");
                    return;
                }
                buildCreatedResponse(accountResourceUri(accountId, TAX_COUNTRY_RESOURCE_NAME), resp);
                return;
            } else if (VATIN_RESOURCE_NAME.equals(resourceName)) {
                VATINRsc vatin;
                try {
                    vatin = JSON_MAPPER.readValue(getRequestData(req), VATINRsc.class);
                } catch (JsonProcessingException exc) {
                    vatin = null;
                }
                if (vatin == null) {
                    resp.sendError(SC_BAD_REQUEST, "Invalid VAT Identification Number resource in request body");
                    return;
                }
                boolean saved = vatinController.saveAccountVatin(accountId, vatin, tenant);
                if (!saved) {
                    resp.sendError(SC_INTERNAL_SERVER_ERROR, "Could not save VAT Identification Number resource");
                    return;
                }
                buildCreatedResponse(accountResourceUri(accountId, VATIN_RESOURCE_NAME), resp);
                return;
            } else {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
        }

        matcher = INVOICE_ITEM_PATTERN.matcher(pathInfo);
        if (matcher.matches()) {
            UUID invoiceItemId = toUUIDOrNull(matcher.group(RESOURCE_IDENTIFIER_GROUP));
            if (invoiceItemId == null) {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
            String resourceName = matcher.group(RESOURCE_NAME_GROUP);
            if (!TAX_CODES_RESOURCE_NAME.equals(resourceName)) {
                buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
                return;
            }
            TaxCodesPUTRsc taxCodesRsc;
            try {
                taxCodesRsc = JSON_MAPPER.readValue(getRequestData(req), TaxCodesPUTRsc.class);
            } catch (JsonProcessingException exc) {
                taxCodesRsc = null;
            }
            if (taxCodesRsc == null) {
                resp.sendError(SC_BAD_REQUEST, "Invalid Tax Codes resource in request body");
                return;
            }
            boolean saved = taxCodeController.saveTaxCodesOfInvoiceItem(invoiceItemId, taxCodesRsc, tenant);
            if (!saved) {
                resp.sendError(SC_INTERNAL_SERVER_ERROR, "Could not save Tax Codes resource");
                return;
            }
            buildCreatedResponse(invoiceItemResourceUri(invoiceItemId, TAX_CODES_RESOURCE_NAME), resp);
            return;
        }

        buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
    }

    private void writeJsonOkResponse(Object value, HttpServletResponse resp) throws IOException {
        byte[] data = JSON_MAPPER.writeValueAsBytes(value);
        setJsonContentType(resp);
        buildOKResponse(data, resp);
    }
}
