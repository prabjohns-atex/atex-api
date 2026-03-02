package com.atex.onecms.app.dam.types;

import java.util.ArrayList;
import java.util.List;

import com.atex.onecms.ace.annotations.AceAspect;
import com.atex.onecms.content.ContentId;

@AceAspect(AceWebMetadata.ASPECT_NAME)
public class AceWebMetadata {

    public static final String ASPECT_NAME = "webMetadata";

    private List<ContentId> relatedSections = new ArrayList<>();
    private String slugName;
    private String slugNameArabic;
    private ContentId parentId;

    public List<ContentId> getRelatedSections() { return relatedSections; }
    public void setRelatedSections(final List<ContentId> relatedSections) { this.relatedSections = relatedSections; }
    public String getSlugName() { return slugName; }
    public void setSlugName(final String slugName) { this.slugName = slugName; }
    public String getSlugNameArabic() { return slugNameArabic; }
    public void setSlugNameArabic(final String slugNameArabic) { this.slugNameArabic = slugNameArabic; }
    public ContentId getParentId() { return parentId; }
    public void setParentId(final ContentId parentId) { this.parentId = parentId; }
}

