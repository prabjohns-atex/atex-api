package com.atex.onecms.app.dam;

public class DamAssigneeBean {

    private String externalId;
    private boolean owner;

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public boolean isOwner() { return owner; }
    public void setOwner(boolean owner) { this.owner = owner; }

    @Override
    public String toString() { return externalId; }
}

