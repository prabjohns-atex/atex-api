package com.atex.desk.api.preview;

import com.atex.onecms.ace.AceWebPreviewAdapter;
import com.atex.onecms.ace.AceWebPreviewServiceChannelConfiguration;
import com.atex.onecms.app.dam.publish.DamPublisherFactory;
import com.atex.onecms.app.dam.publish.PublishingContext;
import com.atex.onecms.app.dam.publish.config.RemoteConfigBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.atex.onecms.preview.PreviewContextImpl;
import com.atex.desk.api.onecms.WorkspaceStorage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.polopoly.user.server.Caller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Orchestrates preview generation by dispatching to the appropriate preview adapter
 * based on the remotes configuration for the content's matched backend.
 */
@Service
public class PreviewService {

    private static final Logger LOG = LoggerFactory.getLogger(PreviewService.class);
    private static final Gson GSON = new Gson();

    private final ContentManager contentManager;
    private final DamPublisherFactory publisherFactory;
    private final WorkspaceStorage workspaceStorage;

    public PreviewService(ContentManager contentManager,
                          @Nullable DamPublisherFactory publisherFactory,
                          WorkspaceStorage workspaceStorage) {
        this.contentManager = contentManager;
        this.publisherFactory = publisherFactory;
        this.workspaceStorage = workspaceStorage;
    }

    /**
     * Generate a preview for the given content.
     *
     * @param contentVersionId the resolved content version
     * @param channel          the preview channel (e.g., "web")
     * @param workspace        optional workspace name
     * @param body             additional request body (may be empty JsonObject)
     * @param userName         the authenticated user name
     * @param subject          the authenticated subject
     * @return JSON response with previewUrl and contentId
     */
    public JsonObject preview(ContentVersionId contentVersionId, String channel,
                              @Nullable String workspace, JsonObject body,
                              String userName, Subject subject) {
        if (publisherFactory == null) {
            return errorResponse("No publisher factory configured", 501);
        }

        ContentId contentId = contentVersionId.getContentId();
        String contentIdStr = IdUtil.toIdString(contentId);

        // Resolve the remote backend configuration
        RemoteConfigBean remoteConfig;
        try {
            Caller caller = new Caller(userName);
            PublishingContext ctx = publisherFactory.createContext(contentId, caller);
            remoteConfig = ctx.getRemoteConfiguration();
        } catch (Exception e) {
            LOG.warn("Failed to resolve remote config for {}: {}", contentIdStr, e.getMessage());
            return errorResponse("No preview backend configured", 501);
        }

        if (remoteConfig == null) {
            return errorResponse("No preview backend configured", 501);
        }

        String adapterClassName = remoteConfig.getPreviewAdapterClassName();

        // Dispatch based on adapter class name
        if (adapterClassName != null && adapterClassName.contains("AceWebPreviewAdapter")) {
            return acePreview(contentVersionId, channel, workspace, body, userName, subject, remoteConfig);
        } else if (adapterClassName != null && adapterClassName.contains("WebPreviewAdapter")) {
            return webPreview(contentVersionId, remoteConfig);
        } else if (adapterClassName == null || adapterClassName.isEmpty()) {
            // No adapter configured — try simple web preview as fallback
            return webPreview(contentVersionId, remoteConfig);
        } else {
            LOG.warn("Unknown preview adapter class: {}", adapterClassName);
            return errorResponse("Unknown preview adapter: " + adapterClassName, 501);
        }
    }

    private JsonObject acePreview(ContentVersionId contentVersionId, String channel,
                                   String workspace, JsonObject body, String userName,
                                   Subject subject, RemoteConfigBean remoteConfig) {
        // Parse the preview config
        AceWebPreviewServiceChannelConfiguration aceConfig = parseAceConfig(remoteConfig);

        // Build context
        PreviewContextImpl<AceWebPreviewServiceChannelConfiguration> context =
            new PreviewContextImpl<>(channel, aceConfig, contentManager, userName, subject);

        // Create and invoke adapter
        AceWebPreviewAdapter adapter = new AceWebPreviewAdapter(
            publisherFactory, workspaceStorage, contentManager);

        return adapter.preview(contentVersionId, workspace, body, context);
    }

    private JsonObject webPreview(ContentVersionId contentVersionId, RemoteConfigBean remoteConfig) {
        String contentIdStr = IdUtil.toIdString(contentVersionId.getContentId());
        String remoteApiUrl = remoteConfig.getRemoteApiUrl();
        if (remoteApiUrl == null || remoteApiUrl.isEmpty()) {
            return errorResponse("Remote API URL not configured", 501);
        }

        // Trim trailing slash
        if (remoteApiUrl.endsWith("/")) {
            remoteApiUrl = remoteApiUrl.substring(0, remoteApiUrl.length() - 1);
        }

        // Read the previewDispatcherUrl from the preview config
        String previewDispatcherUrl = "/preview/";
        Object previewConfigObj = remoteConfig.getPreviewConfig();
        if (previewConfigObj != null) {
            try {
                String configJson = GSON.toJson(previewConfigObj);
                JsonObject configJsonObj = GSON.fromJson(configJson, JsonObject.class);
                if (configJsonObj.has("previewDispatcherUrl")) {
                    previewDispatcherUrl = configJsonObj.get("previewDispatcherUrl").getAsString();
                }
            } catch (Exception e) {
                LOG.debug("Could not parse previewConfig: {}", e.getMessage());
            }
        }

        String previewUrl = remoteApiUrl + previewDispatcherUrl + contentIdStr;

        JsonObject result = new JsonObject();
        result.addProperty("previewUrl", previewUrl);
        result.addProperty("contentId", contentIdStr);
        return result;
    }

    private AceWebPreviewServiceChannelConfiguration parseAceConfig(RemoteConfigBean remoteConfig) {
        Object previewConfigObj = remoteConfig.getPreviewConfig();
        if (previewConfigObj != null) {
            try {
                String json = GSON.toJson(previewConfigObj);
                return GSON.fromJson(json, AceWebPreviewServiceChannelConfiguration.class);
            } catch (Exception e) {
                LOG.warn("Failed to parse ACE preview config: {}", e.getMessage());
            }
        }
        return new AceWebPreviewServiceChannelConfiguration();
    }

    private JsonObject errorResponse(String message, int status) {
        JsonObject result = new JsonObject();
        result.addProperty("error", message);
        result.addProperty("status", status);
        return result;
    }
}
