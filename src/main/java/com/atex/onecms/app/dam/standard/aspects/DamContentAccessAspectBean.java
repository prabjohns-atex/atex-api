package com.atex.onecms.app.dam.standard.aspects;

import java.util.ArrayList;
import java.util.List;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

/**
 * Aspect bean for content access control (creator, assignees).
 */
@AspectDefinition(DamContentAccessAspectBean.ASPECT_NAME)
public class DamContentAccessAspectBean {
    public static final String ASPECT_NAME = "atex.dam.standard.ContentAccess";

    private String creator;
    private List<String> assignees;

    public String getCreator() { return creator; }
    public void setCreator(String v) { this.creator = v; }

    public List<String> getAssignees() { return assignees; }
    public void setAssignees(List<String> v) { this.assignees = v; }

    public List<String> getAssigneeList() {
        if (assignees == null) {
            assignees = new ArrayList<>();
        }
        return assignees;
    }
}
