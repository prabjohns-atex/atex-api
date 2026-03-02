package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import java.util.Date;

@AspectDefinition(DamInstagramAspectBean.ASPECT_NAME)
public class DamInstagramAspectBean extends OneContentBean {

    public static final String ASPECT_NAME = "atex.dam.standard.Instagram";
    public static final String OBJECT_TYPE = "instagram";
    public static final String INPUT_TEMPLATE = "p.DamInstagram";

    public DamInstagramAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        super.setInputTemplate(INPUT_TEMPLATE);
    }

    private String caption = null;
    private String userName;
    private String thumbUrl;
    private String lowUrl;
    private String url;
    private String link;
    private String location;
    private String locationId;
    private double latitude;
    private double longitude;
    private String standardVideoUrl;
    private String lowVideoUrl;

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getThumbUrl() { return thumbUrl; }
    public void setThumbUrl(String thumbUrl) { this.thumbUrl = thumbUrl; }
    public String getLowUrl() { return lowUrl; }
    public void setLowUrl(String lowUrl) { this.lowUrl = lowUrl; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getLocationId() { return locationId; }
    public void setLocationId(String locationId) { this.locationId = locationId; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getStandardVideoUrl() { return standardVideoUrl; }
    public void setStandardVideoUrl(String standardVideoUrl) { this.standardVideoUrl = standardVideoUrl; }
    public String getLowVideoUrl() { return lowVideoUrl; }
    public void setLowVideoUrl(String lowVideoUrl) { this.lowVideoUrl = lowVideoUrl; }
}

