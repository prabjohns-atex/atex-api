package com.atex.plugins.copyfit.operations;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.ArrayList;
import java.util.List;

@AspectDefinition(PrintOperationsAspectBean.ASPECT_NAME)
public class PrintOperationsAspectBean {
    public static final String ASPECT_NAME = "atex.PrintOperations";

    private List<String> operations = null;
    private List<PrintSlotBean> slots = null;

    public List<String> getOperations() {
        if (operations == null) {
            operations = new ArrayList<>();
        }
        return operations;
    }

    public void setOperations(List<String> operations) { this.operations = operations; }

    public void addOperation(String operation) { this.getOperations().add(operation); }

    public List<PrintSlotBean> getSlots() {
        if (slots == null) {
            slots = new ArrayList<>();
        }
        return slots;
    }

    public void setSlots(List<PrintSlotBean> slots) { this.slots = slots; }
}
