package com.atex.onecms.app.dam.workflow;

import java.util.List;

public class WFStatusListBean {
    public static final String ASPECT_NAME = "dam.wfstatuslist.d";

    private List<WFStatusBean> statuses;
    public List<WFStatusBean> getStatuses() { return statuses; }
    public void setStatuses(List<WFStatusBean> v) { this.statuses = v; }
    public List<WFStatusBean> getStatus() { return statuses; }
    public void setStatus(List<WFStatusBean> v) { this.statuses = v; }
}
