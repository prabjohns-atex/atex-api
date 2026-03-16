package com.atex.desk.integration.feed;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed wire image data. Produced by {@link parser.WireArticleParser} when
 * parsing image feeds, consumed by {@link FeedContentCreator}.
 */
public class WireImage {

    private String headline;
    private String caption;
    private String byline;
    private String credit;
    private String source;
    private String category;
    private String keywords;
    private String location;
    private String imageFilename;
    private int width;
    private int height;
    private List<String> tags = new ArrayList<>();

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getByline() { return byline; }
    public void setByline(String byline) { this.byline = byline; }

    public String getCredit() { return credit; }
    public void setCredit(String credit) { this.credit = credit; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getImageFilename() { return imageFilename; }
    public void setImageFilename(String imageFilename) { this.imageFilename = imageFilename; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
