package com.atex.onecms.app.dam.workflow;

public class WebContentStatusAspectBean extends AbstractStatusAspectBean {
    public static final String ASPECT_NAME = "atex.WebContentStatus";

    public WebContentStatusAspectBean() { super(); }
    public WebContentStatusAspectBean(WFStatusBean status) { super(status); }
    public WebContentStatusAspectBean(WFStatusBean status, String comment) { super(status, comment); }
}
