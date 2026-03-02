package com.atex.onecms.preview;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.Subject;

/**
 * Context provided to preview adapters during preview generation.
 *
 * @param <CONFIG> the configuration type for this preview adapter
 */
public interface PreviewContext<CONFIG> {

    String getChannelName();

    CONFIG getConfiguration();

    ContentManager getContentManager();

    String getUserName();

    Subject getSubject();
}
