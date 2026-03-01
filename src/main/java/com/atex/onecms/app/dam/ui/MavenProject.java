package com.atex.onecms.app.dam.ui;

/**
 * Maven project information for module listing.
 */
public class MavenProject {
    private final String groupId;
    private final String artifactId;
    private final String version;

    public MavenProject(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getGroupId() { return groupId; }
    public String getArtifactId() { return artifactId; }
    public String getVersion() { return version; }
}

