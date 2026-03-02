package com.atex.onecms.ace;

import com.atex.onecms.app.dam.publish.DamPublisherFactory;
import com.atex.onecms.app.dam.publish.PublishingContext;
import com.atex.onecms.app.dam.publish.config.RemoteConfigBean;
import com.atex.onecms.app.dam.util.HttpDamUtils;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.WorkspaceResult;
import com.atex.onecms.preview.PreviewAdapter;
import com.atex.onecms.preview.PreviewContext;
import com.atex.desk.api.onecms.WorkspaceStorage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * ACE web preview adapter. Pushes workspace content to a remote ACE backend
 * and calls its preview API to get a preview URL.
 */
public class AceWebPreviewAdapter implements PreviewAdapter<AceWebPreviewServiceChannelConfiguration> {

    private static final Logger LOG = LoggerFactory.getLogger(AceWebPreviewAdapter.class);
    private static final Gson GSON = new Gson();

    private final DamPublisherFactory publisherFactory;
    private final WorkspaceStorage workspaceStorage;
    private final ContentManager contentManager;

    public AceWebPreviewAdapter(DamPublisherFactory publisherFactory,
                                WorkspaceStorage workspaceStorage,
                                ContentManager contentManager) {
        this.publisherFactory = publisherFactory;
        this.workspaceStorage = workspaceStorage;
        this.contentManager = contentManager;
    }

    @Override
    public JsonObject preview(ContentVersionId contentVersionId, String workspace,
                              JsonObject body, PreviewContext<AceWebPreviewServiceChannelConfiguration> context) {
        AceWebPreviewServiceChannelConfiguration config = context.getConfiguration();
        Subject subject = context.getSubject();
        String userName = context.getUserName();
        String channel = context.getChannelName();
        ContentId contentId = contentVersionId.getContentId();
        String contentIdStr = IdUtil.toIdString(contentId);

        // Resolve the remote backend config
        RemoteConfigBean remoteConfig = resolveRemoteConfig(contentId, userName);
        if (remoteConfig == null) {
            return errorResponse("No remote backend configured");
        }

        String remoteApiUrl = remoteConfig.getRemoteApiUrl();
        if (remoteApiUrl == null || remoteApiUrl.isEmpty()) {
            return errorResponse("Remote API URL not configured");
        }

        // Trim trailing slash for URL construction
        if (remoteApiUrl.endsWith("/")) {
            remoteApiUrl = remoteApiUrl.substring(0, remoteApiUrl.length() - 1);
        }

        // Get auth token for the remote backend
        String authToken = getRemoteAuthToken(remoteConfig);

        try {
            // Push workspace drafts to the remote backend
            if (workspace != null && !workspace.isEmpty()) {
                pushWorkspaceDrafts(workspace, remoteApiUrl, authToken, subject);
            }

            // Call the remote preview API
            String previewUrl = callRemotePreview(config, remoteApiUrl, authToken,
                                                   contentIdStr, channel, workspace, body);

            JsonObject result = new JsonObject();
            result.addProperty("previewUrl", previewUrl);
            result.addProperty("contentId", contentIdStr);
            return result;

        } catch (Exception e) {
            LOG.error("ACE preview failed for {}: {}", contentIdStr, e.getMessage(), e);
            return errorResponse("Preview failed: " + e.getMessage());
        }
    }

    private void pushWorkspaceDrafts(String workspace, String remoteApiUrl,
                                      String authToken, Subject subject) {
        Collection<WorkspaceStorage.DraftEntry> drafts = workspaceStorage.getAllDrafts(workspace);
        if (drafts == null || drafts.isEmpty()) {
            LOG.debug("No drafts in workspace {} to push", workspace);
            return;
        }

        for (WorkspaceStorage.DraftEntry draft : drafts) {
            try {
                String draftContentIdStr = draft.contentIdString();
                ContentId draftContentId = IdUtil.fromString(draftContentIdStr);

                // Read the workspace content
                WorkspaceResult<?> wsResult = contentManager.getFromWorkspace(
                    workspace, draftContentId, Object.class, subject);

                if (wsResult == null || wsResult.getStatus().isNotFound()) {
                    LOG.debug("Workspace draft {} not found, skipping", draftContentIdStr);
                    continue;
                }

                // Serialize the content result to JSON
                String json = serializeContentResult(wsResult);

                // Push to remote workspace
                String url = remoteApiUrl + "/content/workspace/" + workspace
                           + "/contentid/" + draftContentIdStr;
                HttpDamUtils.WebServiceResponse response = HttpDamUtils.callDataApiWs(
                    "application/json", json, HttpDamUtils.WebServiceMethod.PUT, url, authToken, null);

                if (response.isError()) {
                    LOG.warn("Failed to push draft {} to remote: {}",
                             draftContentIdStr, response.getErrorMessage());
                }
            } catch (Exception e) {
                LOG.warn("Error pushing draft {} to remote: {}", draft.contentIdString(), e.getMessage());
            }
        }
    }

    private String callRemotePreview(AceWebPreviewServiceChannelConfiguration config,
                                      String remoteApiUrl, String authToken,
                                      String contentIdStr, String channel,
                                      String workspace, JsonObject body) {
        String aceBaseUrl = config.getAcePreviewBaseUrl();

        // If no ACE preview base URL, construct from remote API URL
        if (aceBaseUrl == null || aceBaseUrl.isEmpty()) {
            return remoteApiUrl + "/preview/" + contentIdStr;
        }

        // Trim trailing slash
        if (aceBaseUrl.endsWith("/")) {
            aceBaseUrl = aceBaseUrl.substring(0, aceBaseUrl.length() - 1);
        }

        // Build the preview request URL
        StringBuilder urlBuilder = new StringBuilder(aceBaseUrl)
            .append("/preview/contentid/").append(contentIdStr)
            .append("?channel=").append(channel);
        if (workspace != null && !workspace.isEmpty()) {
            urlBuilder.append("&workspace=").append(workspace);
        }
        String url = urlBuilder.toString();

        String bodyJson = body != null ? GSON.toJson(body) : "{}";
        HttpDamUtils.WebServiceResponse response = HttpDamUtils.callDataApiWs(
            "application/json", bodyJson, HttpDamUtils.WebServiceMethod.POST, url, authToken, null);

        if (response.isError()) {
            LOG.warn("Remote preview call failed: {}", response.getErrorMessage());
            // Fallback: construct a direct preview URL
            return aceBaseUrl + "/preview/" + contentIdStr;
        }

        // Parse response to extract previewUrl
        String responseBody = response.getBody();
        if (responseBody != null && !responseBody.isEmpty()) {
            try {
                JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
                if (responseJson.has("previewUrl")) {
                    return responseJson.get("previewUrl").getAsString();
                }
                if (responseJson.has("url")) {
                    return responseJson.get("url").getAsString();
                }
            } catch (Exception e) {
                LOG.debug("Could not parse preview response as JSON: {}", responseBody);
            }
        }

        // Fallback
        return aceBaseUrl + "/preview/" + contentIdStr;
    }

    @SuppressWarnings("unchecked")
    private String serializeContentResult(ContentResult<?> result) {
        JsonObject json = new JsonObject();

        if (result.getContentId() != null) {
            json.addProperty("id", IdUtil.toIdString(result.getContentId().getContentId()));
            json.addProperty("version", IdUtil.toVersionedIdString(result.getContentId()));
        }

        if (result.getContent() != null) {
            JsonObject aspects = new JsonObject();

            if (result.getContent().getContentData() != null) {
                String dataJson = GSON.toJson(result.getContent().getContentData());
                JsonObject contentData = new JsonObject();
                contentData.addProperty("name", "contentData");
                contentData.add("data", JsonParser.parseString(dataJson));
                aspects.add("contentData", contentData);
            }

            if (result.getContent().getAspects() != null) {
                for (var aspect : result.getContent().getAspects()) {
                    JsonObject aspectObj = new JsonObject();
                    aspectObj.addProperty("name", aspect.getName());
                    if (aspect.getData() != null) {
                        String aspJson = GSON.toJson(aspect.getData());
                        aspectObj.add("data", JsonParser.parseString(aspJson));
                    }
                    aspects.add(aspect.getName(), aspectObj);
                }
            }

            json.add("aspects", aspects);
        }

        return GSON.toJson(json);
    }

    private RemoteConfigBean resolveRemoteConfig(ContentId contentId, String userName) {
        try {
            com.polopoly.user.server.Caller caller = new com.polopoly.user.server.Caller(userName);
            PublishingContext ctx = publisherFactory.createContext(contentId, caller);
            return ctx.getRemoteConfiguration();
        } catch (Exception e) {
            LOG.warn("Could not resolve remote config: {}", e.getMessage());
            return null;
        }
    }

    private String getRemoteAuthToken(RemoteConfigBean config) {
        String user = config.getRemoteUser();
        String password = config.getRemotePassword();
        String apiUrl = config.getRemoteApiUrl();
        if (user != null && password != null && apiUrl != null) {
            return HttpDamUtils.getAuthToken(user, password, apiUrl);
        }
        return null;
    }

    private JsonObject errorResponse(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("error", message);
        return result;
    }
}
