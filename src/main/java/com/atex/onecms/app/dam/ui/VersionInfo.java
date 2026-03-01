package com.atex.onecms.app.dam.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Version info for the About dialog.
 */
public class VersionInfo {

    public static List<UIModuleInfo> getVersions() {
        List<UIModuleInfo> versions = new ArrayList<>();
        versions.add(new UIModuleInfo("desk-api", getImplVersion(), "Desk API (Spring Boot)"));
        return versions;
    }

    public static List<MavenProject> getModules() {
        List<MavenProject> modules = new ArrayList<>();
        modules.add(new MavenProject("com.atex.cloud", "desk-api",
            getImplVersion()));
        return modules;
    }

    private static String getImplVersion() {
        String version = VersionInfo.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    public static class UIModuleInfo {
        private final String name;
        private final String version;
        private final String description;

        public UIModuleInfo(String name, String version, String description) {
            this.name = name;
            this.version = version;
            this.description = description;
        }

        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getDescription() { return description; }
    }
}

