package com.atex.onecms.app.dam.standard.aspects;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import com.atex.onecms.GeoLocation;
import com.atex.onecms.app.dam.types.TimeState;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import com.atex.plugins.structured.text.StructuredText;

@AspectDefinition(OneArticleBean.ASPECT_NAME)
public class OneArticleBean extends OneArchiveBean implements RelatedSectionsAware,
                                                              PublicationLinkSupport,
                                                              PushNotificationSupport,
                                                              PremiumTypeSupport,
                                                              MinorChangeSupport,
                                                              PublishUpdatedTimeAware,
                                                              AuthorsSupport {

    public static final String ASPECT_NAME = "atex.onecms.article";
    public static final String OBJECT_TYPE = "article";
    public static final String INPUT_TEMPLATE_PRODUCTION = "p.NosqlArticle";
    public static final String INPUT_TEMPLATE_ARCHIVE = "p.DamArticle";
    public static final String INPUT_TEMPLATE_WIRE = "p.DamWireArticle";
    public static final String DEFAULT_INPUT_TEMPLATE = INPUT_TEMPLATE_PRODUCTION;

    public OneArticleBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        super.setInputTemplate(DEFAULT_INPUT_TEMPLATE);
    }

    private StructuredText headline;
    private StructuredText lead;
    private StructuredText body;
    private String byline;
    private String ogTitle;
    private String ogDescription;
    private List<ContentId> images;
    private List<ContentId> ogImages;
    private List<ContentId> resources;
    private long publishingTime;
    private long publishingUpdateTime = 0L;
    private boolean premiumContent;
    private int priority = 3;
    private List<GeoLocation> locations;

    private String factsHeading;
    private String factsBody;
    private String teaserHeadline;
    private String teaserText;
    private ContentId teaserImage;
    private String mediaEmbed;
    private int rating;
    private String allowComments;
    private TimeState timeState;

    private long publicationDate = getNextDay();
    private boolean hold = false;
    private boolean printFirst;
    private boolean initialisedFromPrint = false;

    private ContentId topElement;
    private String topMediaCaption;
    private List<ContentId> relatedArticles;
    private String topMediaAltText;
    private List<ContentId> authors;
    private String webArticleType;
    private String publicationLink;
    private String overTitle;

    private StructuredText teaserTitle;
    private StructuredText subTitle;
    private StructuredText caption;

    private String teaserSummary;
    private String socialTitle;
    private String socialDescription;
    private String seoTitle;
    private String seoDescription;

    private String levelId;
    private String keyPhrase;
    private boolean noIndex = false;
    private boolean showAuthorOnWeb = false;
    private String editor;
    private String desk;
    private String budgetHead;
    private String editorialNotes;
    private boolean pushNotification = false;
    private Boolean minorChange;
    private AudioAI audioAI;

    private List<ContentId> linkPath;
    private String premiumType = PremiumTypeSupport.DEFAULT_PREMIUM_TYPE;
    private List<ContentId> relatedSections;
    private Float seoScore;
    private ContentId sponsorLogo;

    private long getNextDay() {
        return Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
    }

    public StructuredText getHeadline() { return headline; }
    public void setHeadline(StructuredText headline) { this.headline = headline; }
    public StructuredText getLead() { return lead; }
    public void setLead(StructuredText lead) { this.lead = lead; }
    public StructuredText getBody() { return body; }
    public void setBody(StructuredText body) { this.body = body; }
    public String getByline() { return byline; }
    public void setByline(String byline) { this.byline = byline; }
    public String getOgTitle() { return ogTitle; }
    public void setOgTitle(String ogTitle) { this.ogTitle = ogTitle; }
    public String getOgDescription() { return ogDescription; }
    public void setOgDescription(String ogDescription) { this.ogDescription = ogDescription; }
    public List<ContentId> getOgImages() { return ogImages; }
    public void setOgImages(final List<ContentId> ogImages) { this.ogImages = ogImages; }
    public List<ContentId> getImages() { return images; }
    public void setImages(final List<ContentId> images) { this.images = images; }
    public List<ContentId> getResources() { return resources; }
    public void setResources(final List<ContentId> resources) { this.resources = resources; }
    public long getPublishingTime() { return publishingTime; }
    public void setPublishingTime(final long publishingTime) { this.publishingTime = publishingTime; }
    @Override public long getPublishingUpdateTime() { return publishingUpdateTime; }
    @Override public void setPublishingUpdateTime(final long publishingUpdateTime) { this.publishingUpdateTime = publishingUpdateTime; }
    public boolean isPremiumContent() { return premiumContent; }
    public void setPremiumContent(final boolean premiumContent) { this.premiumContent = premiumContent; }
    public List<ContentId> getLinkPath() { return linkPath; }
    public void setLinkPath(final List<ContentId> linkPath) { this.linkPath = linkPath; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public List<GeoLocation> getLocations() { return locations; }
    public void setLocations(List<GeoLocation> locations) { this.locations = locations; }
    public String getFactsHeading() { return factsHeading; }
    public void setFactsHeading(String factsHeading) { this.factsHeading = factsHeading; }
    public String getFactsBody() { return factsBody; }
    public void setFactsBody(String factsBody) { this.factsBody = factsBody; }
    public String getTeaserHeadline() { return teaserHeadline; }
    public void setTeaserHeadline(String teaserHeadline) { this.teaserHeadline = teaserHeadline; }
    public String getTeaserText() { return teaserText; }
    public void setTeaserText(final String teaserText) { this.teaserText = teaserText; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getAllowComments() { return allowComments; }
    public void setAllowComments(String allowComments) { this.allowComments = allowComments; }
    public ContentId getTeaserImage() { return teaserImage; }
    public void setTeaserImage(ContentId teaserImage) { this.teaserImage = teaserImage; }
    public String getMediaEmbed() { return mediaEmbed; }
    public void setMediaEmbed(String mediaEmbed) { this.mediaEmbed = mediaEmbed; }
    public TimeState getTimeState() { return timeState; }
    public void setTimeState(final TimeState timeState) { this.timeState = timeState; }
    public long getPublicationDate() { return publicationDate; }
    public void setPublicationDate(long publicationDate) { this.publicationDate = publicationDate; }
    public boolean isHold() { return hold; }
    public void setHold(boolean hold) { this.hold = hold; }
    public boolean isPrintFirst() { return printFirst; }
    public void setPrintFirst(boolean printFirst) { this.printFirst = printFirst; }
    public boolean isInitialisedFromPrint() { return initialisedFromPrint; }
    public void setInitialisedFromPrint(boolean initialisedFromPrint) { this.initialisedFromPrint = initialisedFromPrint; }
    public ContentId getTopElement() { return topElement; }
    public void setTopElement(ContentId topElement) { this.topElement = topElement; }
    public String getTopMediaCaption() { return topMediaCaption; }
    public void setTopMediaCaption(final String topMediaCaption) { this.topMediaCaption = topMediaCaption; }
    public List<ContentId> getRelatedArticles() { return relatedArticles; }
    public void setRelatedArticles(List<ContentId> relatedArticles) { this.relatedArticles = relatedArticles; }
    @Override public List<ContentId> getAuthors() { return authors; }
    @Override public void setAuthors(List<ContentId> authors) { this.authors = authors; }
    @Override public String getPublicationLink() { return publicationLink; }
    @Override public void setPublicationLink(String publicationLink) { this.publicationLink = publicationLink; }
    public String getWebArticleType() { return webArticleType; }
    public void setWebArticleType(final String webArticleType) { this.webArticleType = webArticleType; }
    public String getOverTitle() { return overTitle; }
    public void setOverTitle(String overTitle) { this.overTitle = overTitle; }
    public StructuredText getTeaserTitle() { return teaserTitle; }
    public void setTeaserTitle(StructuredText teaserTitle) { this.teaserTitle = teaserTitle; }
    public StructuredText getSubTitle() { return subTitle; }
    public void setSubTitle(StructuredText subTitle) { this.subTitle = subTitle; }
    public StructuredText getCaption() { return caption; }
    public void setCaption(StructuredText caption) { this.caption = caption; }
    @Deprecated public String getTeaserSummary() { return teaserSummary; }
    @Deprecated public void setTeaserSummary(String teaserSummary) { this.teaserSummary = teaserSummary; }
    public String getSocialTitle() { return socialTitle; }
    public void setSocialTitle(String socialTitle) { this.socialTitle = socialTitle; }
    public String getSocialDescription() { return socialDescription; }
    public void setSocialDescription(String socialDescription) { this.socialDescription = socialDescription; }
    public String getSeoTitle() { return seoTitle; }
    public void setSeoTitle(String seoTitle) { this.seoTitle = seoTitle; }
    public String getSeoDescription() { return seoDescription; }
    public void setSeoDescription(String seoDescription) { this.seoDescription = seoDescription; }
    public String getLevelId() { return levelId; }
    public void setLevelId(final String levelId) { this.levelId = levelId; }
    public String getKeyPhrase() { return keyPhrase; }
    public void setKeyPhrase(String keyPhrase) { this.keyPhrase = keyPhrase; }
    @Override public String getPremiumType() { return premiumType; }
    @Override public void setPremiumType(String premiumType) { this.premiumType = premiumType; }
    public String getEditor() { return editor; }
    public void setEditor(String editor) { this.editor = editor; }
    public String getDesk() { return desk; }
    public void setDesk(String desk) { this.desk = desk; }
    public String getBudgetHead() { return budgetHead; }
    public void setBudgetHead(String budgetHead) { this.budgetHead = budgetHead; }
    public String getEditorialNotes() { return editorialNotes; }
    public void setEditorialNotes(String editorialNotes) { this.editorialNotes = editorialNotes; }
    @Override public boolean isPushNotification() { return pushNotification; }
    @Override public void setPushNotification(final boolean pushNotification) { this.pushNotification = pushNotification; }
    @Override public List<ContentId> getRelatedSections() { return relatedSections; }
    @Override public void setRelatedSections(final List<ContentId> relatedSections) { this.relatedSections = relatedSections; }
    public boolean isNoIndex() { return noIndex; }
    public void setNoIndex(final boolean noIndex) { this.noIndex = noIndex; }
    public boolean isShowAuthorOnWeb() { return showAuthorOnWeb; }
    public void setShowAuthorOnWeb(final boolean showAuthorOnWeb) { this.showAuthorOnWeb = showAuthorOnWeb; }
    public ContentId getSponsorLogo() { return sponsorLogo; }
    public void setSponsorLogo(ContentId sponsorLogo) { this.sponsorLogo = sponsorLogo; }
    public Float getSeoScore() { return seoScore; }
    public void setSeoScore(final Float seoScore) { this.seoScore = seoScore; }
    public String getTopMediaAltText() { return topMediaAltText; }
    public void setTopMediaAltText(final String topMediaAltText) { this.topMediaAltText = topMediaAltText; }
    @Override public Boolean isMinorChange() { return minorChange; }
    @Override public void setMinorChange(final Boolean minorChange) { this.minorChange = minorChange; }
    public AudioAI getAudioAI() { return audioAI; }
    public void setAudioAI(final AudioAI audioAI) { this.audioAI = audioAI; }
}
