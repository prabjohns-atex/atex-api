package com.polopoly.cm;

import java.io.Serializable;

/**
 * Represents a reference to content with optional metadata.
 */
public class ContentReference implements Serializable {
    private ContentId referredContentId;
    private ContentId referenceMetaDataId;

    public ContentReference(ContentId referredContentId) {
        this(referredContentId, null);
    }

    public ContentReference(ContentId referredContentId, ContentId referenceMetaDataId) {
        this.referredContentId = referredContentId;
        this.referenceMetaDataId = referenceMetaDataId;
    }

    public ContentId getReferredContentId() { return referredContentId; }
    public void setReferredContentId(ContentId id) { this.referredContentId = id; }
    public ContentId getReferenceMetaDataId() { return referenceMetaDataId; }
    public void setReferenceMetaDataId(ContentId id) { this.referenceMetaDataId = id; }
}
