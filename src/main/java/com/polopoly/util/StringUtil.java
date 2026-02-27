package com.polopoly.util;

/**
 * String utility methods.
 */
public final class StringUtil {

    private StringUtil() {}

    public static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static boolean notEmpty(String s) {
        return !isEmpty(s);
    }

    public static String notNull(String s) {
        return s != null ? s : "";
    }

    public static boolean equals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    public static boolean equalsIgnoreCase(String a, String b) {
        if (a == null) return b == null;
        return a.equalsIgnoreCase(b);
    }
}
