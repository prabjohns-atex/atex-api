package com.atex.onecms.content;

import com.atex.onecms.app.dam.engagement.EngagementDesc;

/**
 * Operation to add an engagement record to content.
 */
public class AddEngagement implements ContentOperation {

    private final ContentId sourceId;
    private final EngagementDesc engagement;
    private final boolean addOriginalContent;

    public AddEngagement(ContentId sourceId, EngagementDesc engagement) {
        this(sourceId, engagement, false);
    }

    public AddEngagement(ContentId sourceId, EngagementDesc engagement, boolean addOriginalContent) {
        this.sourceId = sourceId;
        this.engagement = engagement;
        this.addOriginalContent = addOriginalContent;
    }

    public ContentId getSourceId() { return sourceId; }
    public EngagementDesc getEngagement() { return engagement; }
    public boolean isAddOriginalContent() { return addOriginalContent; }
}
