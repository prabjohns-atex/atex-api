package com.atex.onecms.app.dam.sendcontent;
import java.util.List;
public class DamSendContentBean {
    private List<String> contentIds;
    private String target;
    public List<String> getContentIds() { return contentIds; }
    public void setContentIds(List<String> v) { this.contentIds = v; }
    public String getTarget() { return target; }
    public void setTarget(String v) { this.target = v; }
}
