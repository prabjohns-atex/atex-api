package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;
import java.util.List;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(DamDocumentAspectBean.ASPECT_NAME)
public class DamDocumentAspectBean extends OneContentBean implements PublicationLinkSupport,
                                                                     DigitalPublishingTimeAware,
                                                                     PublishUpdatedTimeAware {

    public static final String ASPECT_NAME = "atex.dam.standard.Document";
    public static final String OBJECT_TYPE = "document";
    public static final String INPUT_TEMPLATE = "p.Document";

    public DamDocumentAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        super.setInputTemplate(INPUT_TEMPLATE);
    }

    private String headline;
    private String caption;
    private String byline;
    private String description;
    private List<ContentId> posterImage;
    private String filePath;
    private String publicationLink;
    private long digitalPublishingTime = 0L;
    private long publishingUpdateTime = 0L;

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getByline() { return byline; }
    public void setByline(String byline) { this.byline = byline; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<ContentId> getPosterImage() { return posterImage; }
    public void setPosterImage(List<ContentId> posterImage) { this.posterImage = posterImage; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    @Override public String getPublicationLink() { return publicationLink; }
    @Override public void setPublicationLink(final String publicationLink) { this.publicationLink = publicationLink; }
    @Override public long getDigitalPublishingTime() { return digitalPublishingTime; }
    @Override public void setDigitalPublishingTime(long digitalPublishingTime) { this.digitalPublishingTime = digitalPublishingTime; }
    @Override public long getPublishingUpdateTime() { return publishingUpdateTime; }
    @Override public void setPublishingUpdateTime(final long publishingUpdateTime) { this.publishingUpdateTime = publishingUpdateTime; }
}

