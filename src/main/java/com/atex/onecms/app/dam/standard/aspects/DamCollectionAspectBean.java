package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;
import java.util.List;

import com.atex.onecms.app.dam.types.TimeState;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(DamCollectionAspectBean.ASPECT_NAME)
public class DamCollectionAspectBean extends OneArchiveBean implements DigitalPublishingTimeAware,
                                                                       PublishUpdatedTimeAware,
                                                                       TimeStateAware,
                                                                       RelatedSectionsAware,
                                                                       PremiumTypeSupport,
                                                                       PublicationLinkSupport,
                                                                       AuthorsSupport {

    public static final String ASPECT_NAME = "atex.dam.standard.Collection";
    public static final String OBJECT_TYPE = "collection";
    public static final String INPUT_TEMPLATE = "p.Collection";

    public DamCollectionAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        super.setInputTemplate(INPUT_TEMPLATE);
    }

    private String description = null;
    private String headline = null;
    private String status = "0";
    private String datetime = null;
    private String gallery = null;
    private String preview = null;
    private List<ContentId> contents;
    private String defaultCrop = null;
    private List<ContentId> authors;
    private String polopolyURL = null;
    private String urgency = null;
    private long digitalPublishingTime = 0L;
    private Long publishingTime;
    private long publishingUpdateTime = 0L;
    private String homePage = null;
    private String desk = null;
    private TimeState timeState;
    private String caption;
    private String byline;
    private boolean showAuthorOnWeb = false;
    private String editor;
    private List<ContentId> posterImage;
    private List<ContentId> relatedSections;
    private String premiumType = PremiumTypeSupport.DEFAULT_PREMIUM_TYPE;
    private String publicationLink;
    private Long lastUpdated;

    public String getDesk() { return desk; }
    public void setDesk(String desk) { this.desk = desk; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    @Override public long getDigitalPublishingTime() { return digitalPublishingTime; }
    @Override public void setDigitalPublishingTime(final long publishingTime) { this.digitalPublishingTime = publishingTime; }
    public Long getPublishingTime() { return publishingTime; }
    public void setPublishingTime(Long publishingTime) { this.publishingTime = publishingTime; }
    @Override public void setPublishingUpdateTime(final long publishingUpdateTime) { this.publishingUpdateTime = publishingUpdateTime; }
    @Override public long getPublishingUpdateTime() { return publishingUpdateTime; }

    @Deprecated
    public Date getTimeOn() {
        return timeState != null && timeState.getOntime() != null && timeState.getOntime() != 0 ? new Date(timeState.getOntime()) : null;
    }
    @Deprecated
    public void setTimeOn(final Date timeOn) {
        if (timeOn == null) { if (timeState != null) timeState.setOntime(null); }
        else { if (timeState == null) timeState = new TimeState(timeOn.getTime(), null); else timeState.setOntime(timeOn.getTime()); }
    }

    public String getHomePage() { return homePage; }
    public void setHomePage(String homePage) { this.homePage = homePage; }
    public String getPolopolyURL() { return polopolyURL; }
    public void setPolopolyURL(String polopolyURL) { this.polopolyURL = polopolyURL; }
    public List<ContentId> getContents() { return contents; }
    public void setContents(List<ContentId> contents) { this.contents = contents; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getStatus() { return (status != null && !status.trim().isEmpty()) ? status : "0"; }
    public void setStatus(String status) { this.status = (status != null && !status.trim().isEmpty()) ? status : "0"; }
    public String getDatetime() { return datetime; }
    public void setDatetime(String datetime) { this.datetime = datetime; }
    public String getGallery() { return gallery; }
    public void setGallery(String gallery) { this.gallery = gallery; }
    public String getPreview() { return preview; }
    public void setPreview(String preview) { this.preview = preview; }
    public String getDefaultCrop() { return defaultCrop; }
    public void setDefaultCrop(String defaultCrop) { this.defaultCrop = defaultCrop; }
    @Override public List<ContentId> getAuthors() { return authors; }
    @Override public void setAuthors(List<ContentId> authors) { this.authors = authors; }
    @Override public TimeState getTimeState() { return timeState; }
    @Override public void setTimeState(final TimeState timeState) { this.timeState = timeState; }
    public String getCaption() { return caption; }
    public void setCaption(final String caption) { this.caption = caption; }
    public String getByline() { return byline; }
    public void setByline(final String byline) { this.byline = byline; }
    public boolean isShowAuthorOnWeb() { return showAuthorOnWeb; }
    public void setShowAuthorOnWeb(final boolean showAuthorOnWeb) { this.showAuthorOnWeb = showAuthorOnWeb; }
    public String getEditor() { return editor; }
    public void setEditor(String editor) { this.editor = editor; }
    public List<ContentId> getPosterImage() { return posterImage; }
    public void setPosterImage(final List<ContentId> posterImage) { this.posterImage = posterImage; }
    @Override public List<ContentId> getRelatedSections() { return relatedSections; }
    @Override public void setRelatedSections(final List<ContentId> relatedSections) { this.relatedSections = relatedSections; }
    @Override public String getPremiumType() { return premiumType; }
    @Override public void setPremiumType(final String premiumType) { this.premiumType = premiumType; }
    @Override public String getPublicationLink() { return publicationLink; }
    @Override public void setPublicationLink(final String publicationLink) { this.publicationLink = publicationLink; }
    public Long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(final Long lastUpdated) { this.lastUpdated = lastUpdated; }
}
