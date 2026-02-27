package com.atex.onecms.content;

/**
 * Builder for WorkspaceResult instances.
 */
public class WorkspaceResultBuilder<T> {
    private final String workspaceId;
    private final ContentResultBuilder<T> delegate;
    private ContentVersionId draftId;

    public WorkspaceResultBuilder(String workspaceId, ContentResultBuilder<T> delegate) {
        this.workspaceId = workspaceId;
        this.delegate = delegate;
    }

    public WorkspaceResultBuilder<T> draftId(ContentVersionId draftId) {
        this.draftId = draftId;
        return this;
    }

    public WorkspaceResult<T> build() {
        ContentResult<T> raw = delegate.build();
        if (raw.getContent() != null) {
            return new WorkspaceResult<>(
                raw.getContentId(), workspaceId, draftId,
                raw.getContent().getContentDataType(),
                raw.getContent().getContentAspect(),
                raw.getContent().getVariant(),
                raw.getMeta(),
                raw.getContent().getAspects());
        } else {
            return new WorkspaceResult<>(raw.getContentId(), workspaceId, draftId, raw.getStatus());
        }
    }
}
