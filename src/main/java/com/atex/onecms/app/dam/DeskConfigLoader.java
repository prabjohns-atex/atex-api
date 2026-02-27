package com.atex.onecms.app.dam;
public class DeskConfigLoader {
    private DeskConfigLoader() {}
    private static DeskConfig config;
    public static DeskConfig getDeskConfig() { return config != null ? config : new DeskConfig(); }
    public static void setDeskConfig(DeskConfig c) { config = c; }
}
