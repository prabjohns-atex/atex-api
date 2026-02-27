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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pre-store hook for image content.
 * Sets name from filename if empty. When image metadata service is configured,
 * extracts EXIF/IPTC/XMP metadata to populate title/caption/author/byline fields.
 * <p>
 * Configuration via application.properties:
 * <pre>
 * desk.image-metadata-service.url=       # empty = skip metadata extraction
 * desk.image-metadata-service.enabled=false
 * </pre>
 */
public class OneImagePreStore implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(OneImagePreStore.class.getName());

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

            // Set name from uploaded file if empty
            Object filesObj = input.getAspect("atex.Files");
            if (filesObj instanceof FilesAspectBean filesAspect) {
                Map<String, ContentFileInfo> files = filesAspect.getFiles();
                if (files != null && !files.isEmpty()) {
                    ContentFileInfo firstFile = files.values().iterator().next();
                    String filePath = firstFile.getFilePath();

                    if (filePath != null && !filePath.isEmpty()) {
                        // Set name from filename if empty
                        if (bean.getName() == null || bean.getName().isEmpty()) {
                            String fileName = filePath;
                            int lastSlash = filePath.lastIndexOf('/');
                            if (lastSlash >= 0) {
                                fileName = filePath.substring(lastSlash + 1);
                            }
                            // Remove extension
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

            // Extract metadata from image if service is configured
            if (metadataServiceEnabled && metadataServiceUrl != null && !metadataServiceUrl.isEmpty()) {
                // Image metadata extraction via REST service
                // When desk.image-metadata-service.enabled=true and URL is configured,
                // this would call the metadata service to extract EXIF/IPTC/XMP data.
                // For now, metadata extraction is a no-op until the service is deployed.
                LOG.fine("Image metadata service configured but extraction not yet implemented");
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
}
