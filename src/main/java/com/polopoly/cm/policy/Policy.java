package com.polopoly.cm.policy;

import com.polopoly.cm.ContentId;

/**
 * Lightweight policy wrapper around content.
 */
public class Policy {
    private final ContentId contentId;

    public Policy(ContentId contentId) {
        this.contentId = contentId;
    }

    public ContentId getContentId() { return contentId; }
}
