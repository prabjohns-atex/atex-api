package com.atex.desk.api.controller;

import com.atex.onecms.app.dam.publish.DamPublisherFactory;
import com.atex.onecms.app.dam.publish.PublishingContext;
import com.atex.onecms.app.dam.publish.config.RemoteConfigBean;
import com.atex.onecms.app.dam.remote.RemoteUtils;
import com.atex.onecms.app.dam.ws.ContentApiException;
import com.atex.onecms.app.dam.ws.DamUserContext;
import com.polopoly.user.server.Caller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remote search proxy endpoint.
 * Forwards search queries to remote CMS backends via the publishing configuration.
 * Ported from the original DamSearchResource.
 */
@RestController
@RequestMapping("/dam/search")
@Tag(name = "DAM")
public class DamSearchController {

    private static final Logger LOG = Logger.getLogger(DamSearchController.class.getName());

    private final DamPublisherFactory damPublisherFactory;

    public DamSearchController(DamPublisherFactory damPublisherFactory) {
        this.damPublisherFactory = damPublisherFactory;
    }

    @GetMapping("/{core}/select")
    @Operation(summary = "Remote search proxy",
               description = "Forward a Solr query to a remote CMS backend")
    @ApiResponse(responseCode = "200", description = "Search results from remote backend")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "502", description = "Remote backend error")
    public ResponseEntity<?> searchRemote(
            @Parameter(description = "Solr core name") @PathVariable("core") String core,
            @Parameter(description = "Remote backend ID") @RequestParam(value = "backendId", required = false) String backendId,
            HttpServletRequest request) {

        DamUserContext userContext = DamUserContext.from(request);
        userContext.assertLoggedIn();
        Caller caller = userContext.getCaller();
        String authToken = userContext.getAuthToken();

        try {
            // Create publishing context for the backend
            PublishingContext context;
            if (backendId != null && !backendId.isEmpty()) {
                context = damPublisherFactory.createContext(backendId, caller);
            } else {
                context = damPublisherFactory.createContext((com.atex.onecms.content.ContentId) null, caller);
            }

            RemoteConfigBean remoteConfig = context.getRemoteConfiguration();
            if (remoteConfig == null || remoteConfig.getRemoteApiUrl() == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"No remote backend configured\"}");
            }

            // Build remote URL: search/{core}/select?{original query}
            String queryString = request.getQueryString();
            // Remove backendId from query string if present
            if (queryString != null && queryString.contains("backendId=")) {
                queryString = queryString.replaceAll("&?backendId=[^&]*", "")
                    .replaceAll("^&", "");
            }

            String remoteUrl = remoteConfig.getRemoteApiUrl();
            if (!remoteUrl.endsWith("/")) remoteUrl += "/";
            remoteUrl += "search/" + core + "/select";
            if (queryString != null && !queryString.isEmpty()) {
                remoteUrl += "?" + queryString;
            }

            // Forward request to remote
            String responseBody = new RemoteUtils(remoteConfig)
                .callRemoteWs("GET", remoteUrl, authToken != null);

            if (responseBody == null || responseBody.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Empty response from remote backend\"}");
            }

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Remote search proxy failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("{\"error\":\"Remote search failed: " + e.getMessage() + "\"}");
        }
    }
}
