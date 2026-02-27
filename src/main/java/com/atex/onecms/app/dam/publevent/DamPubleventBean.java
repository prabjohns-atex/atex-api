package com.atex.onecms.app.dam.publevent;
public class DamPubleventBean {
    private String eventType;
    private String contentId;
    private String target;
    private long timestamp;
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getContentId() { return contentId; }
    public void setContentId(String v) { this.contentId = v; }
    public String getTarget() { return target; }
    public void setTarget(String v) { this.target = v; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long v) { this.timestamp = v; }
}
