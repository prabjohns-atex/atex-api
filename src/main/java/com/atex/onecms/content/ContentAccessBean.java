package com.atex.onecms.content;

import java.util.List;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

/**
 * Bean for content access control (private flag, owners, readers, writers).
 * Ported from polopoly core/data-api-api.
 */
@AspectDefinition(value = ContentAccessBean.ASPECT_NAME, storeWithMainAspect = true)
public class ContentAccessBean {
    public static final String ASPECT_NAME = "p.ContentAccess";

    private boolean isPrivate;
    private String firstOwner;
    private List<String> owners;
    private List<String> readers;
    private List<String> writers;

    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
    public String getFirstOwner() { return firstOwner; }
    public void setFirstOwner(String firstOwner) { this.firstOwner = firstOwner; }
    public List<String> getOwners() { return owners; }
    public void setOwners(List<String> owners) { this.owners = owners; }
    public List<String> getReaders() { return readers; }
    public void setReaders(List<String> readers) { this.readers = readers; }
    public List<String> getWriters() { return writers; }
    public void setWriters(List<String> writers) { this.writers = writers; }
}
