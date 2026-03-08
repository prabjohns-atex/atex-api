package com.atex.onecms.app.dam.standard.aspects;

import java.util.ArrayList;
import java.util.List;

import com.atex.onecms.app.dam.publevent.DamPubleventBean;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(DamPubleventsAspectBean.ASPECT_NAME)
public class DamPubleventsAspectBean {
    public static final String ASPECT_NAME = "atex.dam.standard.Publevents";

    private List<DamPubleventBean> publevents;
    private List<DamPubleventBean> events;

    public DamPubleventsAspectBean() {
        this.publevents = new ArrayList<>();
    }

    public List<DamPubleventBean> getPublevents() { return publevents; }
    public void setPublevents(List<DamPubleventBean> v) { this.publevents = v; }
    public List<DamPubleventBean> getEvents() { return events; }
    public void setEvents(List<DamPubleventBean> v) { this.events = v; }
}
