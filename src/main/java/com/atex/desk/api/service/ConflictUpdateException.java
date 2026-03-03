package com.atex.desk.api.service;

/**
 * Thrown when an update conflicts with the current version (optimistic locking).
 * The requested previousVersion doesn't match the actual latest version.
 */
public class ConflictUpdateException extends RuntimeException
{
    private final String latestVersion;
    private final String requestedVersion;

    public ConflictUpdateException(String latestVersion, String requestedVersion)
    {
        super("Content has been modified. Current version: " + latestVersion
              + ", requested: " + requestedVersion);
        this.latestVersion = latestVersion;
        this.requestedVersion = requestedVersion;
    }

    public String getLatestVersion() { return latestVersion; }
    public String getRequestedVersion() { return requestedVersion; }
}
