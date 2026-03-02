package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;
import java.util.List;

import com.atex.onecms.app.dam.types.TimeState;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(LiveBlogArticleBean.ASPECT_NAME)
public class LiveBlogArticleBean extends OneArticleBean {

    public static final String ASPECT_NAME = "atex.dam.standard.LiveBlog.article";
    public static final String OBJECT_TYPE = "liveblog";
    public static final String INPUT_TEMPLATE = "p.LiveBlogArticle";

    public LiveBlogArticleBean() {
        super();
        setObjectType(OBJECT_TYPE);
        set_type(ASPECT_NAME);
        setCreationdate(new Date());
        setInputTemplate(DEFAULT_INPUT_TEMPLATE);
    }

    private boolean live = false;
    private TimeState eventDate;
    private List<ContentId> events;
    private long lastEventsUpdate;

    public boolean isLive() { return live; }
    public void setLive(final boolean live) { this.live = live; }
    public TimeState getEventDate() { return eventDate; }
    public void setEventDate(final TimeState eventDate) { this.eventDate = eventDate; }
    public List<ContentId> getEvents() { return events; }
    public void setEvents(final List<ContentId> events) { this.events = events; }
    public long getLastEventsUpdate() { return lastEventsUpdate; }
    public void setLastEventsUpdate(final long lastEventsUpdate) { this.lastEventsUpdate = lastEventsUpdate; }
}

