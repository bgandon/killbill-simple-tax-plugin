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

import static java.util.regex.Pattern.compile;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.killbill.billing.plugin.simpletax.plumbing.SimpleTaxActivator.PLUGIN_NAME;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.killbill.billing.plugin.core.PluginServlet;
import org.killbill.billing.plugin.simpletax.config.http.TaxCountryController.TaxCountryRsc;
import org.killbill.billing.plugin.simpletax.config.http.VatinController.VATINRsc;
import org.killbill.billing.tenant.api.Tenant;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final String TAX_COUNTRIES_PATH = "/taxCountries";
    private static final String VATINS_PATH = "/vatins";
    private static final String ACCOUNT_PARAM_NAME = "account";

    private static final String ACCOUNTS_PATH = "/accounts";
    private static final Pattern ACCOUNT_PATTERN = compile(ACCOUNTS_PATH + "/(\\w+(?:-\\w+){4})/(\\w+)");
    private static final int ACCOUNT_ID_GROUP = 1;
    private static final int RESOURCE_NAME_GROUP = 2;
    private static final String VATIN_RESOURCE_NAME = "vatin";
    private static final String TAX_COUNTRY_RESOURCE_NAME = "taxCountry";

    private static String accountResourceUri(UUID accountId, String resourceName) {
        return PLUGIN_BASE_PATH + ACCOUNTS_PATH + '/' + accountId + '/' + resourceName;
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private TaxCountryController taxCountryController;
    private VatinController vatinController;

    public SimpleTaxServlet(VatinController vatinController, TaxCountryController taxCountryController) {
        super();
        this.taxCountryController = taxCountryController;
        this.vatinController = vatinController;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Tenant tenant = getTenant(req);
        if (tenant == null) {
            buildNotFoundResponse("No tenant specified by the 'X-Killbill-ApiKey'"
                    + " and 'X-Killbill-ApiSecret' headers", resp);
            return;
        }
        String pathInfo = req.getPathInfo();
        Matcher matcher = ACCOUNT_PATTERN.matcher(pathInfo);
        if (matcher.matches()) {
            UUID accountId = toUUIDOrNull(matcher.group(ACCOUNT_ID_GROUP));
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
                Object value = taxCountryController.listTaxCountries(null, tenant, resp);
                writeJsonOkResponse(value, resp);
                return;
            }
            UUID accountId = toUUIDOrNull(account);
            if (accountId == null) {
                resp.sendError(SC_BAD_REQUEST, "Illegal value [" + account + "] for request parameter ["
                        + ACCOUNT_PARAM_NAME + "]");
                return;
            }
            Object value = taxCountryController.listTaxCountries(accountId, tenant, resp);
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
            UUID accountId = toUUIDOrNull(account);
            if (accountId == null) {
                resp.sendError(SC_BAD_REQUEST, "Illegal value [" + account + "] for request parameter ["
                        + ACCOUNT_PARAM_NAME + "]");
                return;
            }
            Object value = vatinController.listVatins(accountId, tenant);
            writeJsonOkResponse(value, resp);
            return;
        }
        buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Tenant tenant = getTenant(req);
        if (tenant == null) {
            buildNotFoundResponse("No tenant specified by the 'X-Killbill-ApiKey'"
                    + " and 'X-Killbill-ApiSecret' headers", resp);
            return;
        }
        String pathInfo = req.getPathInfo();
        Matcher matcher = ACCOUNT_PATTERN.matcher(pathInfo);
        if (!matcher.matches()) {
            buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
            return;
        }
        UUID accountId = toUUIDOrNull(matcher.group(ACCOUNT_ID_GROUP));
        if (accountId == null) {
            buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
            return;
        }
        String resourceName = matcher.group(RESOURCE_NAME_GROUP);
        if (TAX_COUNTRY_RESOURCE_NAME.equals(resourceName)) {
            TaxCountryRsc taxCountry = JSON_MAPPER.readValue(getRequestData(req), TaxCountryRsc.class);
            boolean saved = taxCountryController.saveAccountTaxCountry(accountId, taxCountry, tenant);
            if (!saved) {
                resp.sendError(SC_INTERNAL_SERVER_ERROR, "Could not save tax country");
            }
            buildCreatedResponse(accountResourceUri(accountId, TAX_COUNTRY_RESOURCE_NAME), resp);
            return;
        } else if (VATIN_RESOURCE_NAME.equals(resourceName)) {
            VATINRsc vatin = JSON_MAPPER.readValue(getRequestData(req), VATINRsc.class);
            boolean saved = vatinController.saveAccountVatin(accountId, vatin, tenant);
            if (!saved) {
                resp.sendError(SC_INTERNAL_SERVER_ERROR, "Could not save VAT Identification Number");
            }
            buildCreatedResponse(accountResourceUri(accountId, VATIN_RESOURCE_NAME), resp);
            return;
        }
        buildNotFoundResponse("Resource " + pathInfo + " not found", resp);
    }

    private static UUID toUUIDOrNull(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException exc) {
            return null;
        }
    }

    private void writeJsonOkResponse(Object value, HttpServletResponse resp) throws IOException {
        byte[] data = JSON_MAPPER.writeValueAsBytes(value);
        setJsonContentType(resp);
        buildOKResponse(data, resp);
    }
}
