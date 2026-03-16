package com.atex.onecms.app.dam.workflow;

import java.util.ArrayList;
import java.util.List;

public class WFStatusBean {
    private String status;
    private String statusID;
    private String name;
    private String label;
    private String color;
    private List<String> attributes;

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getStatusID() { return statusID; }
    public void setStatusID(String v) { this.statusID = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
    public String getColor() { return color; }
    public void setColor(String v) { this.color = v; }
    public List<String> getAttributes() { return attributes; }
    public void setAttributes(List<String> attributes) { this.attributes = attributes; }

    public void addAttribute(String attribute) {
        if (attributes == null) attributes = new ArrayList<>();
        attributes.add(attribute);
    }

    public void clearAttributes() {
        if (attributes != null) attributes.clear();
    }
}
