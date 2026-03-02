package com.atex.onecms.app.dam.types;

import com.atex.onecms.ace.annotations.AceAspect;

@AceAspect(AceSlugInfo.ASPECT_NAME)
public class AceSlugInfo {

    public static final String ASPECT_NAME = "aceSlugInfo";

    private String text;
    private String slugId;
    private String customText;
    private boolean custom;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getSlugId() { return slugId; }
    public void setSlugId(String slugId) { this.slugId = slugId; }
    public String getCustomText() { return customText; }
    public void setCustomText(String customText) { this.customText = customText; }
    public boolean isCustom() { return custom; }
    public void setCustom(boolean custom) { this.custom = custom; }

    public String slugText() {
        return text + "-" + slugId;
    }
}

