package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(OneImageBean.ASPECT_NAME)
public class OneImageBean extends OneArchiveBean {

    public static final String ASPECT_NAME = "atex.onecms.image";
    public static final String OBJECT_TYPE = "image";
    public static final String INPUT_TEMPLATE_PRODUCTION = "p.NosqlImage";
    public static final String INPUT_TEMPLATE_ARCHIVE = "p.DamImage";
    public static final String INPUT_TEMPLATE_WIRE = "p.DamWireImage";
    public static final String INPUT_TEMPLATE_DEFAULT = INPUT_TEMPLATE_PRODUCTION;

    public OneImageBean() {
        super.set_type(ASPECT_NAME);
        super.setObjectType(OBJECT_TYPE);
        super.setCreationdate(new Date());
        super.setInputTemplate(INPUT_TEMPLATE_DEFAULT);
    }

    private String title;
    private String caption;
    private String description;
    private String byline;
    private String rights;
    private String webStatement;
    private String licensorURL;
    private long datePhotographTaken;
    private String location;
    private String person;
    private String alternativeText;
    private boolean oneTimeUse;
    private boolean noUsePrint;
    private boolean noUseWeb;
    private String place;
    private String credit;
    private String reporter;
    private String instructions;
    private int width;
    private int height;
    private String imageContentText;
    private String clippingPath;
    private Boolean useWatermark;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getByline() { return byline; }
    public void setByline(String byline) { this.byline = byline; }
    public long getDatePhotographTaken() { return datePhotographTaken; }
    public void setDatePhotographTaken(long datePhotographTaken) { this.datePhotographTaken = datePhotographTaken; }
    public boolean isOneTimeUse() { return oneTimeUse; }
    public void setOneTimeUse(boolean oneTimeUse) { this.oneTimeUse = oneTimeUse; }
    public boolean isNoUsePrint() { return noUsePrint; }
    public void setNoUsePrint(boolean noUsePrint) { this.noUsePrint = noUsePrint; }
    public boolean isNoUseWeb() { return noUseWeb; }
    public void setNoUseWeb(boolean noUseWeb) { this.noUseWeb = noUseWeb; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public String getCredit() { return credit; }
    public void setCredit(String credit) { this.credit = credit; }
    public String getReporter() { return reporter; }
    public void setReporter(String reporter) { this.reporter = reporter; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public String getImageContentText() { return imageContentText; }
    public void setImageContentText(String imageContentText) { this.imageContentText = imageContentText; }
    public String getRights() { return rights; }
    public void setRights(String rights) { this.rights = rights; }
    public String getWebStatement() { return webStatement; }
    public void setWebStatement(String webStatement) { this.webStatement = webStatement; }
    public String getLicensorURL() { return licensorURL; }
    public void setLicensorURL(String licensorURL) { this.licensorURL = licensorURL; }
    public String getAlternativeText() { return alternativeText; }
    public void setAlternativeText(String alternativeText) { this.alternativeText = alternativeText; }
    public String getPerson() { return person; }
    public void setPerson(String person) { this.person = person; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getClippingPath() { return clippingPath; }
    public void setClippingPath(String clippingPath) { this.clippingPath = clippingPath; }
    public Boolean getUseWatermark() { return useWatermark; }
    public void setUseWatermark(final Boolean useWatermark) { this.useWatermark = useWatermark; }
}
