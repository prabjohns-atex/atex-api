package com.atex.onecms.app.dam.standard.aspects;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.atex.onecms.app.dam.types.TimeState;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(DamVideoAspectBean.ASPECT_NAME)
public class DamVideoAspectBean extends OneArchiveBean implements DigitalPublishingTimeAware,
                                                                  PublishUpdatedTimeAware,
                                                                  TimeStateAware,
                                                                  RelatedSectionsAware,
                                                                  PremiumTypeSupport,
                                                                  TranscribeSupport,
                                                                  BinaryUrlSupport,
                                                                  PublicationLinkSupport,
                                                                  AuthorsSupport {

    public static final String ASPECT_NAME = "atex.dam.standard.Video";
    private static final String OBJECT_TYPE = "video";
    public static final String INPUT_TEMPLATE = "p.DamVideo";

    public DamVideoAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        super.setInputTemplate(INPUT_TEMPLATE);
    }

    private String videolevel = null;
    private int width = 0;
    private int height = 0;
    private String headline = null;
    private String description = null;
    private String caption = null;
    private String brightcoveId = null;
    private String videoPath = null;
    private List<ContentId> resources = new ArrayList<>();
    private List<ContentId> authors;
    private String polopolyUrl = null;
    private String urgency = null;
    private long digitalPublishingTime = 0L;
    private Long publishingTime;
    private long publishingUpdateTime = 0L;
    private TimeState timeState;
    private String homepage = null;
    private String byline;
    private boolean showAuthorOnWeb = false;
    private List<ContentId> relatedSections;
    private List<ContentId> images;
    private String editor;
    private String desk;
    private long publicationDate = 0L;
    private String premiumType = PremiumTypeSupport.DEFAULT_PREMIUM_TYPE;
    private String transcriptionText;
    private boolean transcribeFlag;
    private String binaryUrl;
    private String publicationLink;

    public String getPolopolyUrl() { return polopolyUrl; }
    public void setPolopolyUrl(String polopolyUrl) { this.polopolyUrl = polopolyUrl; }
    public String getVideolevel() { return videolevel; }
    public void setVideolevel(String videolevel) { this.videolevel = videolevel; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getBrightcoveId() { return brightcoveId; }
    public void setBrightcoveId(String brightcoveId) { this.brightcoveId = brightcoveId; }
    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }
    public List<ContentId> getResources() { return resources; }
    public void setResources(final List<ContentId> resources) { this.resources = resources; }
    @Override public List<ContentId> getAuthors() { return authors; }
    @Override public void setAuthors(List<ContentId> authors) { this.authors = authors; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    @Override public void setDigitalPublishingTime(final long publishingTime) { this.digitalPublishingTime = publishingTime; }
    @Override public long getDigitalPublishingTime() { return digitalPublishingTime; }
    public Long getPublishingTime() { return publishingTime; }
    public void setPublishingTime(Long publishingTime) { this.publishingTime = publishingTime; }
    @Override public long getPublishingUpdateTime() { return publishingUpdateTime; }
    @Override public void setPublishingUpdateTime(final long publishingUpdateTime) { this.publishingUpdateTime = publishingUpdateTime; }
    @Deprecated
    public Date getTimeOn() {
        return timeState != null && timeState.getOntime() != null && timeState.getOntime() != 0 ? new Date(timeState.getOntime()) : null;
    }
    @Deprecated
    public void setTimeOn(Date timeOn) {
        if (timeOn == null) { if (timeState != null) timeState.setOntime(null); }
        else { if (timeState == null) timeState = new TimeState(timeOn.getTime(), null); else timeState.setOntime(timeOn.getTime()); }
    }
    @Override public TimeState getTimeState() { return timeState; }
    @Override public void setTimeState(final TimeState timeState) { this.timeState = timeState; }
    public String getHomepage() { return homepage; }
    public void setHomepage(String homepage) { this.homepage = homepage; }
    public String getByline() { return byline; }
    public void setByline(final String byline) { this.byline = byline; }
    public String getEditor() { return editor; }
    public void setEditor(String editor) { this.editor = editor; }
    public String getDesk() { return desk; }
    public void setDesk(String desk) { this.desk = desk; }
    public long getPublicationDate() { return publicationDate; }
    public void setPublicationDate(long publicationDate) { this.publicationDate = publicationDate; }
    public boolean isShowAuthorOnWeb() { return showAuthorOnWeb; }
    public void setShowAuthorOnWeb(boolean showAuthorOnWeb) { this.showAuthorOnWeb = showAuthorOnWeb; }
    @Override public List<ContentId> getRelatedSections() { return relatedSections; }
    @Override public void setRelatedSections(final List<ContentId> relatedSections) { this.relatedSections = relatedSections; }
    public List<ContentId> getImages() { return images; }
    public void setImages(final List<ContentId> images) { this.images = images; }
    @Override public String getPremiumType() { return premiumType; }
    @Override public void setPremiumType(final String premiumType) { this.premiumType = premiumType; }
    @Override public String getTranscriptionText() { return transcriptionText; }
    @Override public void setTranscriptionText(final String transcriptionText) { this.transcriptionText = transcriptionText; }
    @Override public Boolean isTranscribeFlag() { return transcribeFlag; }
    @Override public void setTranscribeFlag(final Boolean transcribeFlag) { this.transcribeFlag = transcribeFlag; }
    @Override public String getBinaryUrl() { return binaryUrl; }
    @Override public void setBinaryUrl(String binaryUrl) { this.binaryUrl = binaryUrl; }
    @Override public String getPublicationLink() { return publicationLink; }
    @Override public void setPublicationLink(final String publicationLink) { this.publicationLink = publicationLink; }
}

