package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Deprecated
@AspectDefinition("atex.dam.standard.Article")
public class DamArticleAspectBean extends DamArchiveAspectBean {

    private static final String OBJECT_TYPE = "article";
    private static final String INPUT_TEMPLATE = "p.DamArticle";
    public static final String ASPECT_NAME = "atex.dam.standard.Article";

    private String headline = null;
    private String body = null;
    private String author = null;
    private List<ContentId> resources = new ArrayList<>();
    private String subject = null;

    public DamArticleAspectBean() {
        this.setObjectType(OBJECT_TYPE);
        this.setInputTemplate(INPUT_TEMPLATE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
    }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public List<ContentId> getResources() { return resources; }
    public void setResources(final List<ContentId> resources) { this.resources = resources; }

    @Override
    public String getObjectType() { return OBJECT_TYPE; }
    @Override
    public void setObjectType(String objectType) { super.setObjectType(OBJECT_TYPE); }
    @Override
    public String getInputTemplate() { return INPUT_TEMPLATE; }
    @Override
    public void setInputTemplate(String inputTemplate) { super.setInputTemplate(INPUT_TEMPLATE); }
}
