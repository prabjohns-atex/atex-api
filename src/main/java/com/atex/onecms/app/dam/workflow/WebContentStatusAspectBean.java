package com.atex.onecms.app.dam.workflow;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(WebContentStatusAspectBean.ASPECT_NAME)
public class WebContentStatusAspectBean extends AbstractStatusAspectBean {
    public static final String ASPECT_NAME = "atex.WebContentStatus";

    public WebContentStatusAspectBean() { super(); }
    public WebContentStatusAspectBean(WFStatusBean status) { super(status); }
    public WebContentStatusAspectBean(WFStatusBean status, String comment) { super(status, comment); }
}
