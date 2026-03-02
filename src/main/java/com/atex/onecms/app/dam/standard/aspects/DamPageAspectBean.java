package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import java.util.Date;

@AspectDefinition(DamPageAspectBean.ASPECT_NAME)
public class DamPageAspectBean extends OneArchiveBean {

    public static final String ASPECT_NAME = "atex.dam.standard.Page";
    public static final String OBJECT_TYPE = "page";
    public static final String INPUT_TEMPLATE_PRODUCTION = "p.OnePage";
    public static final String INPUT_TEMPLATE_ARCHIVE = "p.DamPage";
    public static final String DEFAULT_INPUT_TEMPLATE = INPUT_TEMPLATE_PRODUCTION;

    public DamPageAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        super.setInputTemplate(DEFAULT_INPUT_TEMPLATE);
    }

    private String description = null;
    private String body = null;
    private int width = 0;
    private int height = 0;

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}

