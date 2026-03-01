package com.atex.onecms.app.dam.util;

/**
 * Simple string utility used by DamPrintPageResource.
 */
public final class StringUtils {

    private StringUtils() {}

    public static boolean notNull(String s) {
        return s != null && !s.trim().isEmpty();
    }
}

