package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(value = PrintDeadlineAspectBean.ASPECT_NAME)
public class PrintDeadlineAspectBean {
    public static final String ASPECT_NAME = "atex.print.Deadline";

    private long deadline = 0;
    private long deadlineWarning = 0;

    public long getDeadline() { return deadline; }
    public void setDeadline(long deadline) { this.deadline = deadline; }
    public long getDeadlineWarning() { return deadlineWarning; }
    public void setDeadlineWarning(long deadlineWarning) { this.deadlineWarning = deadlineWarning; }
}

