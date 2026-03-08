package com.atex.onecms.app.dam.workflow;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(WFContentStatusAspectBean.ASPECT_NAME)
public class WFContentStatusAspectBean extends AbstractStatusAspectBean {
    public static final String ASPECT_NAME = "atex.WFContentStatus";

    public WFContentStatusAspectBean() { super(); }
    public WFContentStatusAspectBean(WFStatusBean status) { super(status); }
    public WFContentStatusAspectBean(WFStatusBean status, String comment) { super(status, comment); }
}
