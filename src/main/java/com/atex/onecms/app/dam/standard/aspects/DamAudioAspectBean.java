package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;
import java.util.List;

import com.atex.onecms.app.dam.types.TimeState;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(DamAudioAspectBean.ASPECT_NAME)
public class DamAudioAspectBean extends OneArchiveBean implements DigitalPublishingTimeAware,
                                                                  PublishUpdatedTimeAware,
                                                                  TimeStateAware,
                                                                  RelatedSectionsAware,
                                                                  PremiumTypeSupport,
                                                                  TranscribeSupport,
                                                                  BinaryUrlSupport,
                                                                  PublicationLinkSupport,
                                                                  AuthorsSupport {

    public static final String ASPECT_NAME = "atex.dam.standard.Audio";
    public static final String OBJECT_TYPE = "audio";
    public static final String INPUT_TEMPLATE = "p.DamAudio";

    public DamAudioAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        super.setInputTemplate(INPUT_TEMPLATE);
    }

    private String headline = null;
    private String body = null;
    private String summary = null;
    private String byline;
    private String caption;
    private String description;
    private String audioLink = null;
    private String mediaId = null;
    private String shortHeadline = null;
    private String editorialNotes = null;
    private String urgency = null;
    private long digitalPublishingTime = 0L;
    private long publishingUpdateTime = 0L;
    private TimeState timeState;
    private String homepage = null;
    private Boolean premiumContent;
    private List<ContentId> relatedSections;
    private List<ContentId> images;
    private List<ContentId> authors;
    private String premiumType = PremiumTypeSupport.DEFAULT_PREMIUM_TYPE;
    private String transcriptionText;
    private Boolean transcribeFlag;
    private String binaryUrl;
    private String publicationLink;

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    @Override public long getDigitalPublishingTime() { return digitalPublishingTime; }
    @Override public void setDigitalPublishingTime(long digitalPublishingTime) { this.digitalPublishingTime = digitalPublishingTime; }
    @Override public long getPublishingUpdateTime() { return publishingUpdateTime; }
    @Override public void setPublishingUpdateTime(final long publishingUpdateTime) { this.publishingUpdateTime = publishingUpdateTime; }

    @Deprecated
    public Date getTimeOn() {
        return timeState != null && timeState.getOntime() != null && timeState.getOntime() != 0 ? new Date(timeState.getOntime()) : null;
    }
    @Deprecated
    public void setTimeOn(final Date timeOn) {
        if (timeOn == null) { if (timeState != null) timeState.setOntime(null); }
        else { if (timeState == null) timeState = new TimeState(timeOn.getTime(), null); else timeState.setOntime(timeOn.getTime()); }
    }

    public String getHomepage() { return homepage; }
    public void setHomepage(String homepage) { this.homepage = homepage; }
    public String getEditorialNotes() { return editorialNotes; }
    public void setEditorialNotes(String editorialNotes) { this.editorialNotes = editorialNotes; }
    public String getShortHeadline() { return shortHeadline; }
    public void setShortHeadline(String shortHeadline) { this.shortHeadline = shortHeadline; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getByline() { return byline; }
    public void setByline(final String byline) { this.byline = byline; }
    public String getCaption() { return caption; }
    public void setCaption(final String caption) { this.caption = caption; }
    public String getDescription() { return description; }
    public void setDescription(final String description) { this.description = description; }
    public String getAudioLink() { return audioLink; }
    public void setAudioLink(String audioLink) { this.audioLink = audioLink; }
    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }
    @Override public TimeState getTimeState() { return timeState; }
    @Override public void setTimeState(final TimeState timeState) { this.timeState = timeState; }
    public Boolean getPremiumContent() { return premiumContent; }
    public void setPremiumContent(final Boolean premiumContent) { this.premiumContent = premiumContent; }
    @Override public List<ContentId> getRelatedSections() { return relatedSections; }
    @Override public void setRelatedSections(final List<ContentId> relatedSections) { this.relatedSections = relatedSections; }
    public List<ContentId> getImages() { return images; }
    public void setImages(final List<ContentId> images) { this.images = images; }
    @Override public List<ContentId> getAuthors() { return authors; }
    @Override public void setAuthors(final List<ContentId> authors) { this.authors = authors; }
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
