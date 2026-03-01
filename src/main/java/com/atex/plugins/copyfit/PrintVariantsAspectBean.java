package com.atex.plugins.copyfit;

import java.util.ArrayList;
import java.util.List;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition("atex.PrintVariants")
public class PrintVariantsAspectBean {
    private int shapeId;
    private int docId;
    private String printId;
    private String lastPollTime;
    private String shapeFilter;
    private int changeId;
    private int stateFlags;
    private PrintWorkflowStatus status;
    private List<PrintAttribute> attributes = new ArrayList<>();
    private String auxData = "{}";

    public int getShapeId() { return shapeId; }
    public void setShapeId(int shapeId) { this.shapeId = shapeId; }

    public int getDocId() { return docId; }
    public void setDocId(int docId) { this.docId = docId; }

    public String getPrintId() { return printId; }
    public void setPrintId(String printId) { this.printId = printId; }

    public String getLastPollTime() { return lastPollTime; }
    public void setLastPollTime(String lastPollTime) { this.lastPollTime = lastPollTime; }

    public String getShapeFilter() { return shapeFilter; }
    public void setShapeFilter(String shapeFilter) { this.shapeFilter = shapeFilter; }

    public int getChangeId() { return changeId; }
    public void setChangeId(int changeId) { this.changeId = changeId; }

    public int getStateFlags() { return stateFlags; }
    public void setStateFlags(int stateFlags) { this.stateFlags = stateFlags; }

    public PrintWorkflowStatus getStatus() { return status; }
    public void setStatus(PrintWorkflowStatus status) { this.status = status; }

    public List<PrintAttribute> getAttributes() { return attributes; }
    public void setAttributes(List<PrintAttribute> attributes) { this.attributes = attributes; }

    public String getAuxData() { return auxData; }
    public void setAuxData(String auxData) { this.auxData = auxData; }
}
