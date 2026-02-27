package com.atex.onecms.app.dam.camel.client;

import com.atex.onecms.app.dam.DeskConfig;
import com.atex.onecms.app.dam.camel.configuration.DamRoutesListBean;
import com.atex.onecms.app.dam.util.HttpDamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelApiWebClient {

    private static final Logger LOG = LoggerFactory.getLogger(CamelApiWebClient.class);

    private final DeskConfig config;
    private final String authToken;

    public CamelApiWebClient(DeskConfig config, String authToken) {
        this.config = config;
        this.authToken = authToken;
    }

    public void updateCamelRules(DamRoutesListBean bean) {
        LOG.info("updateCamelRules: {} routes", bean != null ? "some" : "null");
        // Camel integration not yet available
    }

    public String getCamelRoutes(String userId) {
        String url = getCamelApiUrl();
        if (url == null || url.isEmpty()) {
            return "{}";
        }
        try {
            HttpDamUtils.WebServiceResponse response = HttpDamUtils.callDataApiWs(
                "application/json", null, HttpDamUtils.WebServiceMethod.GET,
                url + "/routes?userId=" + userId, authToken, null);
            return response.isError() ? "{}" : response.getBody();
        } catch (Exception e) {
            LOG.error("Error getting camel routes for {}", userId, e);
            return "{}";
        }
    }

    public void addCamelRoute(String fromUri, String toUri) {
        String url = getCamelApiUrl();
        if (url == null || url.isEmpty()) {
            LOG.info("addCamelRoute: no camel API URL configured");
            return;
        }
        String json = "{\"fromUri\":\"" + fromUri + "\",\"toUri\":\"" + toUri + "\"}";
        try {
            HttpDamUtils.callDataApiWs("application/json", json,
                HttpDamUtils.WebServiceMethod.POST, url + "/routes", authToken, null);
        } catch (Exception e) {
            LOG.error("Error adding camel route", e);
        }
    }

    public void stopCamelRoute(String routeId) {
        String url = getCamelApiUrl();
        if (url == null || url.isEmpty()) {
            LOG.info("stopCamelRoute: no camel API URL configured");
            return;
        }
        String json = "{\"routeId\":\"" + routeId + "\"}";
        try {
            HttpDamUtils.callDataApiWs("application/json", json,
                HttpDamUtils.WebServiceMethod.POST, url + "/routes/stop", authToken, null);
        } catch (Exception e) {
            LOG.error("Error stopping camel route {}", routeId, e);
        }
    }

    private String getCamelApiUrl() {
        return config != null ? config.getCamelApiUrl() : null;
    }
}
