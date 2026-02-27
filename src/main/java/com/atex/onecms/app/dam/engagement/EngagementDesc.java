package com.atex.onecms.app.dam.engagement;

import java.util.ArrayList;
import java.util.List;

public class EngagementDesc {
    private String id;
    private String type;
    private String appPk;
    private String appType;
    private String userName;
    private String timestamp;
    private List<EngagementElement> elements;
    private List<EngagementElement> attributes;

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getAppPk() { return appPk; }
    public void setAppPk(String v) { this.appPk = v; }
    public String getAppType() { return appType; }
    public void setAppType(String v) { this.appType = v; }
    public String getUserName() { return userName; }
    public void setUserName(String v) { this.userName = v; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String v) { this.timestamp = v; }
    public List<EngagementElement> getElements() { return elements; }
    public void setElements(List<EngagementElement> v) { this.elements = v; }
    public List<EngagementElement> getAttributes() {
        if (attributes == null) { attributes = new ArrayList<>(); }
        return attributes;
    }
    public void setAttributes(List<EngagementElement> v) { this.attributes = v; }
}
