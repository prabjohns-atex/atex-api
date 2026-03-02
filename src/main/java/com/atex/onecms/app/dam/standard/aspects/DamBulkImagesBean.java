package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;
import java.util.List;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(DamBulkImagesBean.ASPECT_NAME)
public class DamBulkImagesBean extends OneArchiveBean {

    public static final String ASPECT_NAME = "atex.dam.standard.BulkImages";
    private static final String OBJECT_TYPE = "bulkimages";
    private static final String INPUT_TEMPLATE = "p.BulkImages";

    public DamBulkImagesBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        super.setInputTemplate(INPUT_TEMPLATE);
    }

    private String description = null;
    private String headline = null;
    private String caption;
    public List<ContentId> contents;

    public String getDescription() { return description; }
    public void setDescription(final String description) { this.description = description; }
    public String getHeadline() { return headline; }
    public void setHeadline(final String headline) { this.headline = headline; }
    public String getCaption() { return caption; }
    public void setCaption(final String caption) { this.caption = caption; }
    public List<ContentId> getContents() { return contents; }
    public void setContents(final List<ContentId> contents) { this.contents = contents; }
}

