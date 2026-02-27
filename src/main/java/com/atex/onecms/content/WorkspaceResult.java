package com.atex.onecms.content;

import java.util.Collection;
import com.atex.onecms.content.aspects.Aspect;

/**
 * Result from a workspace operation.
 */
public class WorkspaceResult<T> extends ContentResult<T> {
    private final String workspaceId;
    private final ContentVersionId draftId;

    public WorkspaceResult(ContentVersionId id, String workspaceId,
                           ContentVersionId draftId, Status status) {
        super(status, id);
        this.workspaceId = workspaceId;
        this.draftId = draftId;
    }

    public WorkspaceResult(ContentVersionId id, String workspaceId, ContentVersionId draftId,
                           String type, Aspect<T> contentData, String variant,
                           Meta meta, Collection<Aspect> aspects) {
        super(id, type, contentData, variant, meta, aspects);
        this.workspaceId = workspaceId;
        this.draftId = draftId;
    }

    public String getWorkspaceId() { return workspaceId; }
    public ContentVersionId getDraftId() { return draftId; }
}
