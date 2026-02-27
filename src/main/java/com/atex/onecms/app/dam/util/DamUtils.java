package com.atex.onecms.app.dam.util;
import com.atex.onecms.content.Subject;
public class DamUtils {
    private static String damUrl;
    private static String slackModuleUrl;
    private static String apiUrl;
    private static String previewUrl;
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);
    public static String getDamUrl() { return damUrl; }
    public static void setDamUrl(String v) { damUrl = v; }
    public static String getSlackModuleUrl() { return slackModuleUrl; }
    public static void setSlackModuleUrl(String v) { slackModuleUrl = v; }
    public static String getApiUrl() { return apiUrl; }
    public static void setApiUrl(String v) { apiUrl = v; }
    public static String getPreviewUrl() { return previewUrl; }
    public static void setPreviewUrl(String v) { previewUrl = v; }
    public static Subject getSystemSubject() { return SYSTEM_SUBJECT; }
}
