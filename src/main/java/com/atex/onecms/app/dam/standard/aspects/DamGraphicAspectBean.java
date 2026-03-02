package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import java.util.Date;

@AspectDefinition(DamGraphicAspectBean.ASPECT_NAME)
public class DamGraphicAspectBean extends OneArchiveBean {

    public static final String ASPECT_NAME = "atex.dam.standard.Graphic";
    public static final String OBJECT_TYPE = "graphic";
    public static final String INPUT_TEMPLATE_ARCHIVE = "p.DamGraphic";
    public static final String INPUT_TEMPLATE_WIRE = "p.DamWireGraphic";
    public static final String DEFAULT_INPUT_TEMPLATE = INPUT_TEMPLATE_ARCHIVE;

    public DamGraphicAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        super.setInputTemplate(DEFAULT_INPUT_TEMPLATE);
    }

    private String headline;
    private String description;
    private int width = 0;
    private int height = 0;

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
}

