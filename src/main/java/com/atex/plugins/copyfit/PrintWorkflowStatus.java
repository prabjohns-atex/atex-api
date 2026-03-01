package com.atex.plugins.copyfit;

public class PrintWorkflowStatus {
    private int statusId;
    private String statusName;
    private int sequenceNo;
    private String bkColour;
    private String fgColour;
    private int workflowId;
    private String workflowName;

    public int getStatusId() { return statusId; }
    public void setStatusId(int statusId) { this.statusId = statusId; }

    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }

    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }

    public String getBkColour() { return bkColour; }
    public void setBkColour(String bkColour) { this.bkColour = bkColour; }

    public String getFgColour() { return fgColour; }
    public void setFgColour(String fgColour) { this.fgColour = fgColour; }

    public int getWorkflowId() { return workflowId; }
    public void setWorkflowId(int workflowId) { this.workflowId = workflowId; }

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }
}
