package com.atex.desk.integration.feed;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed wire article data. Produced by {@link parser.WireArticleParser} implementations,
 * consumed by {@link FeedContentCreator} to create content in the CMS.
 * Replaces the agency-specific article beans (ReutersArticle, AFPArticle, etc.)
 * with a unified intermediate representation.
 */
public class WireArticle {

    private String headline;
    private String lead;
    private String body;
    private String source;
    private String author;
    private String byline;
    private String priority;
    private String newsId;
    private List<String> tags = new ArrayList<>();
    private List<String> locations = new ArrayList<>();
    private List<String> imageFilenames = new ArrayList<>();

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getLead() { return lead; }
    public void setLead(String lead) { this.lead = lead; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getByline() { return byline; }
    public void setByline(String byline) { this.byline = byline; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getNewsId() { return newsId; }
    public void setNewsId(String newsId) { this.newsId = newsId; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getLocations() { return locations; }
    public void setLocations(List<String> locations) { this.locations = locations; }

    public List<String> getImageFilenames() { return imageFilenames; }
    public void setImageFilenames(List<String> imageFilenames) { this.imageFilenames = imageFilenames; }
}
