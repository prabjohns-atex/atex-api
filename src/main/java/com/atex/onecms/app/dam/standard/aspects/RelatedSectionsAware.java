package com.atex.onecms.app.dam.standard.aspects;

import java.util.List;
import com.atex.onecms.content.ContentId;

public interface RelatedSectionsAware {
    List<ContentId> getRelatedSections();
    void setRelatedSections(final List<ContentId> relatedSections);
}

