package com.atex.onecms.app.dam.remote;

import com.atex.onecms.app.dam.util.HttpDamUtils;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteUtils.class);

    private final Object remoteConfig;

    public RemoteUtils(Object remoteConfig) {
        this.remoteConfig = remoteConfig;
    }

    public ContentResult<?> copyRemoteImage(Subject subject, ContentManager cm, String contentId, Object fileService) {
        return null;
    }

    /**
     * Call a remote web service endpoint.
     *
     * @param method HTTP method (GET, POST, etc.)
     * @param url    the URL to call
     * @param auth   whether to include authentication
     * @return the response body as a string
     */
    public String callRemoteWs(String method, String url, boolean auth) {
        try {
            HttpDamUtils.WebServiceMethod wsMethod = HttpDamUtils.WebServiceMethod.valueOf(method.toUpperCase());
            HttpDamUtils.WebServiceResponse response = HttpDamUtils.callDataApiWs(
                "application/json", null, wsMethod, url, null, null);
            if (response.isError()) {
                LOG.warn("Remote WS call failed: {} {} -> {}", method, url, response.getErrorMessage());
                return "";
            }
            return response.getBody() != null ? response.getBody() : "";
        } catch (Exception e) {
            LOG.error("Error calling remote WS: {} {}", method, url, e);
            return "";
        }
    }
}
