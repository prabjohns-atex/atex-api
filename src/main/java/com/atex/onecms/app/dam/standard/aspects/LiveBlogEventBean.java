package com.atex.onecms.app.dam.standard.aspects;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(LiveBlogEventBean.ASPECT_NAME)
public class LiveBlogEventBean extends OneContentBean {

    public static final String ASPECT_NAME = "atex.dam.standard.LiveBlog.event";
    public static final String OBJECT_TYPE = "liveblogevent";
    public static final String INPUT_TEMPLATE = "p.LiveBlogEvent";

    private String headline = null;
    private String description = null;
    private boolean relevant = false;
    private long date;
    private List<ContentId> images = new ArrayList<>();
    private List<ContentId> resources = new ArrayList<>();

    public LiveBlogEventBean() {
        setObjectType(OBJECT_TYPE);
        set_type(ASPECT_NAME);
        setCreationdate(new Date());
        setInputTemplate(INPUT_TEMPLATE);
    }

    public String getHeadline() { return headline; }
    public void setHeadline(final String headline) { this.headline = headline; }
    public String getDescription() { return description; }
    public void setDescription(final String description) { this.description = description; }
    public boolean isRelevant() { return relevant; }
    public void setRelevant(final boolean relevant) { this.relevant = relevant; }
    public long getDate() { return date; }
    public void setDate(final long date) { this.date = date; }
    public List<ContentId> getImages() { return images; }
    public void setImages(final List<ContentId> images) { this.images = images; }
    public List<ContentId> getResources() { return resources; }
    public void setResources(final List<ContentId> resources) { this.resources = resources; }
}

