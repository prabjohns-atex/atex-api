package com.atex.onecms.app.dam.types;

import com.atex.onecms.ace.annotations.AceAspect;

@AceAspect(AceMigrationInfo.ASPECT_NAME)
public class AceMigrationInfo {

    public static final String ASPECT_NAME = "aceMigrationInfo";

    private String legacyId;
    private String legacyUrl;

    public String getLegacyId() { return legacyId; }
    public void setLegacyId(final String legacyId) { this.legacyId = legacyId; }
    public String getLegacyUrl() { return legacyUrl; }
    public void setLegacyUrl(final String legacyUrl) { this.legacyUrl = legacyUrl; }
}

