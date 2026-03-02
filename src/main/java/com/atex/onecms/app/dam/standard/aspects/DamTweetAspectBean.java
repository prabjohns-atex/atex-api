package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.app.dam.twitter.TweetMediaEntity;
import com.atex.onecms.app.dam.twitter.TweetUrlEntity;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@AspectDefinition(DamTweetAspectBean.ASPECT_NAME)
public class DamTweetAspectBean extends OneContentBean {

    public static final String ASPECT_NAME = "atex.dam.standard.Tweet";
    public static final String OBJECT_TYPE = "tweet";
    public static final String INPUT_TEMPLATE = "p.DamTweet";

    public DamTweetAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.setInputTemplate(INPUT_TEMPLATE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
    }

    private String body = null;
    private String twitterUrl = null;
    public List<TweetUrlEntity> urlEntities = new ArrayList<>();
    public List<TweetMediaEntity> mediaEntities = new ArrayList<>();

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getTwitterUrl() { return twitterUrl; }
    public void setTwitterUrl(String twitterUrl) { this.twitterUrl = twitterUrl; }
    public List<TweetMediaEntity> getMediaEntities() { return mediaEntities; }
    public void setMediaEntities(List<TweetMediaEntity> mediaEntities) { this.mediaEntities = mediaEntities; }
    public List<TweetUrlEntity> getUrlEntities() { return urlEntities; }
    public void setUrlEntities(List<TweetUrlEntity> urlEntities) { this.urlEntities = urlEntities; }
}

