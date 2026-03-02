package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.app.dam.DamContentBean;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@Deprecated
@AspectDefinition("atex.dam.standard.WireImage")
public class DamWireImageAspectBean extends DamContentBean {

    private static final String OBJECT_TYPE = "image";
    private static final String INPUT_TEMPLATE = "p.DamWireImage";
    public static final String ASPECT_NAME = "atex.dam.standard.WireImage";

    private String newsId = null;
    private String headline = null;
    private String body = null;
    private String source = null;
    private String section = null;
    private int width = 0;
    private int height = 0;

    public DamWireImageAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.setInputTemplate(INPUT_TEMPLATE);
        super.set_type(ASPECT_NAME);
    }

    public String getNewsId() { return newsId; }
    public void setNewsId(String newsId) { this.newsId = newsId; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
}
