package com.atex.onecms.app.dam.standard.aspects;

import java.util.ArrayList;
import java.util.List;

/**
 * Aspect bean for content access control (creator, assignees).
 */
public class DamContentAccessAspectBean {
    public static final String ASPECT_NAME = "atex.dam.contentAccess";

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
