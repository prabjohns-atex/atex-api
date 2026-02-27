package com.atex.onecms.content.metadata;

import java.util.HashSet;
import java.util.Set;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import com.polopoly.metadata.Metadata;

/**
 * Metadata info aspect bean.
 */
@AspectDefinition("atex.Metadata")
public class MetadataInfo {
    private Metadata metadata;
    private Set<String> taxonomyIds;

    public MetadataInfo() {
        this.taxonomyIds = new HashSet<>();
    }

    public Metadata getMetadata() { return metadata; }
    public void setMetadata(Metadata metadata) { this.metadata = metadata; }
    public Set<String> getTaxonomyIds() { return taxonomyIds; }
    public void setTaxonomyIds(Set<String> taxonomyIds) { this.taxonomyIds = taxonomyIds; }
}
