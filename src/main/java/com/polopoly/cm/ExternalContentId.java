package com.polopoly.cm;

/**
 * External content ID that uses a string identifier.
 */
public class ExternalContentId extends VersionedContentId {
    private final String externalId;

    public ExternalContentId(String externalId) {
        super(0, 0, UNDEFINED_VERSION);
        this.externalId = externalId;
    }

    public ExternalContentId(String externalId, int version) {
        super(0, 0, version);
        this.externalId = externalId;
    }

    public String getExternalId() { return externalId; }

    @Override
    public boolean isSymbolicId() { return true; }

    @Override
    public String getContentIdString() {
        return externalId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExternalContentId)) return false;
        return externalId.equals(((ExternalContentId) o).externalId);
    }

    @Override
    public int hashCode() {
        return externalId.hashCode();
    }

    @Override
    public String toString() {
        return externalId;
    }
}
