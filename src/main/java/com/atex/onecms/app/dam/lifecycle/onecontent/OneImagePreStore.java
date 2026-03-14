package com.atex.onecms.app.dam.lifecycle.onecontent;

import com.atex.onecms.app.dam.standard.aspects.OneImageBean;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentFileInfo;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.FilesAspectBean;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;
import com.atex.onecms.image.exif.MetadataTagsAspectBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Pre-store hook for image content.
 * Sets name from filename if empty. When image metadata service is configured,
 * extracts EXIF/IPTC/XMP metadata to populate title/description/byline fields
 * and stores the full metadata as an atex.ImageMetadata aspect.
 */
public class OneImagePreStore implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(OneImagePreStore.class.getName());
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final String metadataServiceUrl;
    private final boolean metadataServiceEnabled;

    public OneImagePreStore() {
        this(null, false);
    }

    public OneImagePreStore(String metadataServiceUrl, boolean metadataServiceEnabled) {
        this.metadataServiceUrl = metadataServiceUrl;
        this.metadataServiceEnabled = metadataServiceEnabled;
    }

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        Object data = input.getContentData();
        if (!(data instanceof OneImageBean bean)) {
            return input;
        }

        try {
            boolean modified = false;
            String fileUri = null;

            // Get file info from atex.Files aspect
            Object filesObj = input.getAspect("atex.Files");
            if (filesObj instanceof FilesAspectBean filesAspect) {
                Map<String, ContentFileInfo> files = filesAspect.getFiles();
                if (files != null && !files.isEmpty()) {
                    ContentFileInfo firstFile = files.values().iterator().next();
                    String filePath = firstFile.getFilePath();
                    fileUri = firstFile.getFileUri();

                    if (filePath != null && !filePath.isEmpty()) {
                        // Set name from filename if empty
                        if (bean.getName() == null || bean.getName().isEmpty()) {
                            String fileName = filePath;
                            int lastSlash = filePath.lastIndexOf('/');
                            if (lastSlash >= 0) {
                                fileName = filePath.substring(lastSlash + 1);
                            }
                            int dot = fileName.lastIndexOf('.');
                            if (dot > 0) {
                                fileName = fileName.substring(0, dot);
                            }
                            bean.setName(fileName);
                            modified = true;
                        }
                    }
                }
            }

            // Extract metadata from image via metadata service
            if (metadataServiceEnabled && metadataServiceUrl != null && !metadataServiceUrl.isEmpty()
                    && fileUri != null && !fileUri.isEmpty()) {
                MetadataTagsAspectBean metadata = extractMetadata(fileUri);
                if (metadata != null) {
                    // Populate bean fields from extracted metadata (only if not already set)
                    if ((bean.getTitle() == null || bean.getTitle().isEmpty())
                            && metadata.getTitle() != null) {
                        bean.setTitle(metadata.getTitle());
                        modified = true;
                    }
                    if ((bean.getDescription() == null || bean.getDescription().isEmpty())
                            && metadata.getDescription() != null) {
                        bean.setDescription(metadata.getDescription());
                        modified = true;
                    }
                    if ((bean.getByline() == null || bean.getByline().isEmpty())
                            && metadata.getByline() != null) {
                        bean.setByline(metadata.getByline());
                        modified = true;
                    }
                    if (metadata.getImageWidth() != null) {
                        bean.setWidth(metadata.getImageWidth());
                        modified = true;
                    }
                    if (metadata.getImageHeight() != null) {
                        bean.setHeight(metadata.getImageHeight());
                        modified = true;
                    }

                    // Add metadata aspect to content
                    ContentWriteBuilder<Object> builder = ContentWriteBuilder.from(input);
                    builder.mainAspectData(bean);
                    builder.aspect(MetadataTagsAspectBean.ASPECT_NAME, metadata);
                    return builder.build();
                }
            }

            if (modified) {
                return ContentWriteBuilder.from(input).mainAspectData(bean).build();
            }
            return input;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in OneImagePreStore", e);
            return input;
        }
    }

    /**
     * Calls the image metadata extractor service to extract EXIF/IPTC/XMP data.
     * Uses the GET endpoint which fetches the image from the file service internally.
     */
    private MetadataTagsAspectBean extractMetadata(String fileUri) {
        try {
            // The extractor GET endpoint: /extractor/image/{fileUri}
            String url = metadataServiceUrl + "/extractor/image/" + fileUri;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                if (body != null && !body.isEmpty()) {
                    JsonObject json = GSON.fromJson(body, JsonObject.class);
                    return parseMetadataResponse(json);
                }
            } else {
                LOG.warning("Metadata service returned " + response.statusCode()
                    + " for " + fileUri);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to extract metadata for " + fileUri, e);
        }
        return null;
    }

    /**
     * Parses the metadata service JSON response into a MetadataTagsAspectBean.
     * The response contains top-level fields (imageWidth, imageHeight, title, byline, description)
     * and a tags map with grouped metadata (EXIF, IPTC, XMP, File, etc.).
     */
    private MetadataTagsAspectBean parseMetadataResponse(JsonObject json) {
        MetadataTagsAspectBean bean = new MetadataTagsAspectBean();

        // Direct fields
        if (json.has("imageWidth") && !json.get("imageWidth").isJsonNull()) {
            bean.setImageWidth(json.get("imageWidth").getAsInt());
        }
        if (json.has("imageHeight") && !json.get("imageHeight").isJsonNull()) {
            bean.setImageHeight(json.get("imageHeight").getAsInt());
        }
        if (json.has("title") && !json.get("title").isJsonNull()) {
            bean.setTitle(json.get("title").getAsString());
        }
        if (json.has("byline") && !json.get("byline").isJsonNull()) {
            bean.setByline(json.get("byline").getAsString());
        }
        if (json.has("description") && !json.get("description").isJsonNull()) {
            bean.setDescription(json.get("description").getAsString());
        }

        // Tags map — all other groups (EXIF, IPTC, XMP, File, etc.)
        if (json.has("tags") && json.get("tags").isJsonObject()) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, ?>> tags = GSON.fromJson(json.get("tags"),
                new com.google.gson.reflect.TypeToken<Map<String, Map<String, ?>>>() {}.getType());
            bean.setTags(tags);
        }

        return bean;
    }
}
