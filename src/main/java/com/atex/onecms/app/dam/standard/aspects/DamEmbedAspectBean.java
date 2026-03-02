package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import java.util.Date;

@AspectDefinition(DamEmbedAspectBean.ASPECT_NAME)
public class DamEmbedAspectBean extends OneContentBean {

    public static final String ASPECT_NAME = "atex.dam.standard.Embed";
    public static final String OBJECT_TYPE = "embed";
    public static final String INPUT_TEMPLATE = "p.Embed";

    public DamEmbedAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        super.setInputTemplate(INPUT_TEMPLATE);
    }

    private String html;

    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }
}

