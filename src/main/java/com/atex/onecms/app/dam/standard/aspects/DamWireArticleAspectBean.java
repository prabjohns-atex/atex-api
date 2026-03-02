package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.app.dam.DamContentBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Deprecated
@AspectDefinition("atex.dam.standard.WireArticle")
public class DamWireArticleAspectBean extends DamContentBean {

    private static final String OBJECT_TYPE = "article";
    private static final String INPUT_TEMPLATE = "p.DamWireArticle";
    public static final String ASPECT_NAME = "atex.dam.standard.WireArticle";

    private String newsId = null;
    private String headline = null;
    private String body = null;
    private String source = null;
    private String section = null;
    private List<ContentId> resources = new ArrayList<>();

    public DamWireArticleAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.setInputTemplate(INPUT_TEMPLATE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
    }

    public String getNewsId() { return newsId; }
    public void setNewsId(String newsId) { this.newsId = newsId; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public List<ContentId> getResources() { return resources; }
    public void setResources(final List<ContentId> resources) { this.resources = resources; }
}

