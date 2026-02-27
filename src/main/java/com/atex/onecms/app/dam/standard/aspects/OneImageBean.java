package com.atex.onecms.app.dam.standard.aspects;

public class OneImageBean extends OneContentBean {
    private String description;
    private String caption;
    private String title;
    private String byline;
    private String credit;
    private int width;
    private int height;

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getCaption() { return caption; }
    public void setCaption(String v) { this.caption = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getByline() { return byline; }
    public void setByline(String v) { this.byline = v; }
    public String getCredit() { return credit; }
    public void setCredit(String v) { this.credit = v; }
    public int getWidth() { return width; }
    public void setWidth(int v) { this.width = v; }
    public int getHeight() { return height; }
    public void setHeight(int v) { this.height = v; }
}
