package com.polopoly.application;

/**
 * Root interface for application module system.
 */
public interface Application {

    ApplicationComponent getApplicationComponent(String name);
}
