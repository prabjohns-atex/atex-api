package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.app.dam.DamAssigneeBean;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Deprecated
@AspectDefinition("atex.dam.standard.ContentShare")
public class DamContentShareAspectBean extends DamArchiveAspectBean {

    private static final String OBJECT_TYPE = "contentshare";
    private static final String INPUT_TEMPLATE = "p.DamContentShare";
    public static final String ASPECT_NAME = "atex.dam.standard.ContentShare";

    public List<DamAssigneeBean> assignees = new ArrayList<>();
    private String referringContentId;
    private String creator;

    public DamContentShareAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.setInputTemplate(INPUT_TEMPLATE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
    }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public String getReferringContentId() { return referringContentId; }
    public void setReferringContentId(String referringContentId) { this.referringContentId = referringContentId; }
    public List<DamAssigneeBean> getAssignees() { return assignees; }
    public void setAssignees(List<DamAssigneeBean> assignees) { this.assignees = assignees; }
}

