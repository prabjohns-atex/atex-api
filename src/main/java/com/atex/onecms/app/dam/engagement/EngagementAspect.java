package com.atex.onecms.app.dam.engagement;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.ArrayList;
import java.util.List;

@AspectDefinition({"atex.dam.engagement"})
public class EngagementAspect {
    public static final String ASPECT_NAME = "atex.dam.engagement";

    private List<EngagementDesc> engagements;

    public List<EngagementDesc> getEngagements() { return engagements; }
    public void setEngagements(List<EngagementDesc> v) { this.engagements = v; }

    public List<EngagementDesc> getEngagementList() {
        if (engagements == null) {
            engagements = new ArrayList<>();
        }
        return engagements;
    }
}
