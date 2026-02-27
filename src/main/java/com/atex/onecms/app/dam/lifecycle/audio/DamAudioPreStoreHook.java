package com.atex.onecms.app.dam.lifecycle.audio;

import com.atex.onecms.app.dam.standard.aspects.DamAudioAspectBean;
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
 * Pre-store hook for audio content. Sets name and audioLink
 * from the uploaded file if not already set.
 */
public class DamAudioPreStoreHook implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(DamAudioPreStoreHook.class.getName());

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        Object data = input.getContentData();
        if (!(data instanceof DamAudioAspectBean bean)) {
            return input;
        }

        try {
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
                            bean.setName(fileName);
                        }

                        // Set audioLink from file URI if empty
                        if (bean.getAudioLink() == null || bean.getAudioLink().isEmpty()) {
                            String fileUri = firstFile.getFileUri();
                            if (fileUri != null && !fileUri.isEmpty()) {
                                bean.setAudioLink(fileUri);
                            }
                        }
                    }
                }
            }

            return ContentWriteBuilder.from(input).mainAspectData(bean).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in DamAudioPreStoreHook", e);
            return input;
        }
    }
}
