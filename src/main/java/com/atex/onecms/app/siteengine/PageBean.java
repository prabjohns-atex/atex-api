package com.atex.onecms.app.siteengine;

import java.util.ArrayList;
import java.util.List;

import com.atex.onecms.content.ContentId;

/**
 * Bean class that represents a Page.
 * Ported from polopoly, using OneCMS ContentId instead of Polopoly ContentId.
 */
public class PageBean {
    private String name;
    private String pathSegment;
    private List<ContentId> feeds;
    private List<ContentId> stylesheets;
    private List<ContentId> subPages;

    public PageBean() {
        this(null, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public PageBean(String name, List<ContentId> feeds,
                    List<ContentId> stylesheets, List<ContentId> subPages) {
        this(name, null, feeds, stylesheets, subPages);
    }

    public PageBean(String name, String pathSegment, List<ContentId> feeds,
                    List<ContentId> stylesheets, List<ContentId> subPages) {
        this.name = name;
        this.pathSegment = pathSegment;
        this.feeds = feeds;
        this.stylesheets = stylesheets;
        this.subPages = subPages;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPathSegment() { return pathSegment; }
    public void setPathSegment(String pathSegment) { this.pathSegment = pathSegment; }

    public List<ContentId> getFeeds() { return feeds; }
    public void setFeeds(List<ContentId> feeds) { this.feeds = feeds; }

    public List<ContentId> getStylesheets() { return stylesheets; }
    public void setStylesheets(List<ContentId> stylesheets) { this.stylesheets = stylesheets; }

    public List<ContentId> getSubPages() { return subPages; }
    public void setSubPages(List<ContentId> subPages) { this.subPages = subPages; }
}
