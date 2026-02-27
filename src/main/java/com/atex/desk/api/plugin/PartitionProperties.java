package com.atex.desk.api.plugin;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for partition-to-security-parent mapping.
 * Used by SecParentPreStoreHook to bidirectionally sync partitions and security parents.
 */
@ConfigurationProperties(prefix = "desk.partitions")
public class PartitionProperties {

    /**
     * Map of partition ID to security parent external ID.
     * Example: default=site.default.d
     */
    private Map<String, String> mapping = new HashMap<>();

    public Map<String, String> getMapping() { return mapping; }
    public void setMapping(Map<String, String> mapping) { this.mapping = mapping; }

    /**
     * Reverse lookup: find partition ID for a security parent external ID.
     */
    public String getPartitionForSecurityParent(String securityParentExternalId) {
        if (securityParentExternalId == null) return null;
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (securityParentExternalId.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get the security parent external ID for a partition.
     */
    public String getSecurityParentForPartition(String partitionId) {
        return mapping.get(partitionId);
    }
}
