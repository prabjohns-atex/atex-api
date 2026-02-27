package com.atex.onecms.app.dam.sendcontent;
import java.util.List;
public class DamSendContentRequest {
    private List<String> contentIds;
    private String handler;
    private String target;
    public List<String> getContentIds() { return contentIds; }
    public void setContentIds(List<String> v) { this.contentIds = v; }
    public String getHandler() { return handler; }
    public void setHandler(String v) { this.handler = v; }
    public String getTarget() { return target; }
    public void setTarget(String v) { this.target = v; }
}
