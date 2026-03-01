package com.atex.plugins.copyfit;

import java.util.ArrayList;
import java.util.List;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition("atex.ReconcileAttributes")
public class ReconcileAttributesAspectBean {
    private List<AttributeContent> attributes = new ArrayList<>();

    public List<AttributeContent> getAttributes() { return attributes; }
    public void setAttributes(List<AttributeContent> attributes) { this.attributes = attributes; }
}
