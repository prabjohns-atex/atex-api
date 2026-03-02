package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(DamCollectionLinkAspectBean.ASPECT_NAME)
public class DamCollectionLinkAspectBean {

    public static final String ASPECT_NAME = "atex.dam.standard.CollectionLink";
    private String collectionId;
    private String resourceId;

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
}

