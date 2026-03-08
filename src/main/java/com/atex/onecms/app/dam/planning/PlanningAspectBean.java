package com.atex.onecms.app.dam.planning;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Planning aspect bean for editorial workflow.
 * Ported from gong/onecms-common/beans.
 */
@AspectDefinition("atex.Planning")
public class PlanningAspectBean {
    public static final String ASPECT_NAME = "atex.Planning";

    private String name;
    private ContentId jobId;
    private String taskType;
    private Date deadline;
    private String notes;
    private int articleLength = 0;
    private String imageAspectRatio;
    @Deprecated
    private List<String> assignees = new ArrayList<>();
    private String subType;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ContentId getJobId() { return jobId; }
    public void setJobId(ContentId jobId) { this.jobId = jobId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Date getDeadline() { return deadline; }
    public void setDeadline(Date deadline) { this.deadline = deadline; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public int getArticleLength() { return articleLength; }
    public void setArticleLength(int articleLength) { this.articleLength = articleLength; }
    public String getImageAspectRatio() { return imageAspectRatio; }
    public void setImageAspectRatio(String imageAspectRatio) { this.imageAspectRatio = imageAspectRatio; }
    @Deprecated
    public List<String> getAssignees() { return assignees; }
    @Deprecated
    public void setAssignees(List<String> assignees) { this.assignees = assignees; }
    public String getSubType() { return subType; }
    public void setSubType(String subType) { this.subType = subType; }
}
