package com.atex.onecms.content;

/**
 * Identifies a content.
 */
public final class ContentId {
    private final String delegationId;
    private final String key;

    public ContentId(String delegationId, String key) {
        this.delegationId = delegationId;
        this.key = key;
    }

    public String getDelegationId() {
        return delegationId;
    }

    public String getKey() {
        return key;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((delegationId == null) ? 0 : delegationId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ContentId other = (ContentId) obj;
        if (key == null) {
            if (other.key != null) return false;
        } else if (!key.equals(other.key)) return false;
        if (delegationId == null) {
            if (other.delegationId != null) return false;
        } else if (!delegationId.equals(other.delegationId)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "ContentId [delegationId=" + delegationId + ", key=" + key + "]";
    }
}
