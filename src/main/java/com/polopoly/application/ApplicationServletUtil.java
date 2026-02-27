package com.polopoly.application;

/**
 * Stub — provides static access to Application singleton.
 * Returns null in desk-api context; callers should use Spring injection instead.
 */
public final class ApplicationServletUtil {

    private static volatile Application application;

    private ApplicationServletUtil() {}

    public static Application getApplication() {
        return application;
    }

    public static void setApplication(Application app) {
        application = app;
    }
}
