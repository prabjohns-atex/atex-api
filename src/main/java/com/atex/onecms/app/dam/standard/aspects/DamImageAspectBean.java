package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.Date;

@Deprecated
@AspectDefinition("atex.dam.standard.Image")
public class DamImageAspectBean extends DamArchiveAspectBean {

    private static final String OBJECT_TYPE = "image";
    private static final String INPUT_TEMPLATE = "p.DamImage";
    public static final String ASPECT_NAME = "atex.dam.standard.Image";

    private boolean important = false;
    private String caption = null;
    private String description = null;
    private String headline = null;
    private String author = null;
    private int width = 0;
    private int height = 0;
    private String subject = null;

    public DamImageAspectBean() {
        this.setObjectType(OBJECT_TYPE);
        this.setInputTemplate(INPUT_TEMPLATE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
    }

    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    @Override
    public String getObjectType() { return OBJECT_TYPE; }
    @Override
    public void setObjectType(String objectType) { super.setObjectType(OBJECT_TYPE); }
    @Override
    public String getInputTemplate() { return INPUT_TEMPLATE; }
    @Override
    public void setInputTemplate(String inputTemplate) { super.setInputTemplate(INPUT_TEMPLATE); }
}
