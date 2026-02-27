package com.atex.onecms.app.dam.workflow;

public abstract class AbstractStatusAspectBean {
    private WFStatusBean status;
    private String comment;

    public AbstractStatusAspectBean() {}

    public AbstractStatusAspectBean(WFStatusBean status) {
        this.status = status;
    }

    public AbstractStatusAspectBean(WFStatusBean status, String comment) {
        this.status = status;
        this.comment = comment;
    }

    public WFStatusBean getStatus() { return status; }
    public void setStatus(WFStatusBean status) { this.status = status; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
