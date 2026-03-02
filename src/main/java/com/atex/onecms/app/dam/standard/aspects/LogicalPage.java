package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.ContentId;

public class LogicalPage {
    private String fullName;
    private String statusName;
    private String statusColor;
    private ContentId contentId;
    private Boolean virtual;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }
    public String getStatusColor() { return statusColor; }
    public void setStatusColor(String statusColor) { this.statusColor = statusColor; }
    public ContentId getContentId() { return contentId; }
    public void setContentId(ContentId contentId) { this.contentId = contentId; }
    public Boolean getVirtual() { return virtual; }
    public void setVirtual(Boolean virtual) { this.virtual = virtual; }
}

