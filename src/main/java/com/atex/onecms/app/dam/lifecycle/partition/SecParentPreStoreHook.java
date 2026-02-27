package com.atex.onecms.app.dam.lifecycle.partition;

import com.atex.desk.api.plugin.PartitionProperties;
import com.atex.onecms.app.dam.standard.aspects.OneContentBean;
import com.atex.onecms.content.CachingFetcher;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Entity;
import com.polopoly.metadata.Metadata;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pre-store hook for bidirectional sync between partition dimension and security parent.
 * When partition changes, updates security parent; when security parent changes, updates partition.
 */
public class SecParentPreStoreHook implements LifecyclePreStore<Object, Object> {

    private static final Logger LOG = Logger.getLogger(SecParentPreStoreHook.class.getName());
    private static final String PARTITION_DIMENSION_ID = "dimension.partition";
    private static final String METADATA_ASPECT_NAME = "p.Metadata";

    private final PartitionProperties partitionProperties;

    public SecParentPreStoreHook(PartitionProperties partitionProperties) {
        this.partitionProperties = partitionProperties;
    }

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input, Content<Object> existing,
                                          LifecycleContextPreStore<Object> context)
            throws CallbackException {
        if (partitionProperties == null || partitionProperties.getMapping().isEmpty()) {
            return input;
        }

        Object data = input.getContentData();
        if (!(data instanceof OneContentBean)) {
            return input;
        }

        try {
            ContentManager cm = context.getContentManager();
            Subject subject = context.getSubject();

            // Get current security parent from InsertionInfoAspectBean
            Object iiObj = input.getAspect(InsertionInfoAspectBean.ASPECT_NAME);
            InsertionInfoAspectBean insertionInfo = null;
            if (iiObj instanceof InsertionInfoAspectBean ii) {
                insertionInfo = ii;
            }

            // Get current partition from metadata
            Object metaObj = input.getAspect(METADATA_ASPECT_NAME);
            Metadata metadata = null;
            if (metaObj instanceof Metadata m) {
                metadata = m;
            }

            String currentPartition = getPartitionFromMetadata(metadata);
            String currentSecParentExtId = getSecParentExternalId(insertionInfo, cm, subject);

            // Determine if partition was changed
            String previousPartition = null;
            if (existing != null) {
                Object prevMetaObj = existing.getAspectData(METADATA_ASPECT_NAME);
                if (prevMetaObj instanceof Metadata prevMeta) {
                    previousPartition = getPartitionFromMetadata(prevMeta);
                }
            }

            boolean partitionChanged = currentPartition != null
                && !currentPartition.equals(previousPartition);
            boolean secParentChanged = false;

            if (existing != null) {
                Object prevIiObj = existing.getAspectData(InsertionInfoAspectBean.ASPECT_NAME);
                if (prevIiObj instanceof InsertionInfoAspectBean prevIi) {
                    String prevSecParentExt = getSecParentExternalId(prevIi, cm, subject);
                    secParentChanged = currentSecParentExtId != null
                        && !currentSecParentExtId.equals(prevSecParentExt);
                }
            }

            ContentWriteBuilder<Object> builder = ContentWriteBuilder.from(input);
            boolean modified = false;

            // Partition changed → update security parent
            if (partitionChanged && currentPartition != null) {
                String secParentExtId = partitionProperties.getSecurityParentForPartition(currentPartition);
                if (secParentExtId != null) {
                    CachingFetcher fetcher = CachingFetcher.create(cm, subject);
                    ContentVersionId vid = fetcher.resolve(secParentExtId, subject);
                    if (vid != null) {
                        if (insertionInfo == null) {
                            insertionInfo = new InsertionInfoAspectBean();
                        }
                        insertionInfo.setSecurityParentId(vid.getContentId());
                        builder.aspect(InsertionInfoAspectBean.ASPECT_NAME, insertionInfo);
                        modified = true;
                    }
                }
            }

            // Security parent changed → update partition
            if (secParentChanged && currentSecParentExtId != null && !modified) {
                String partition = partitionProperties.getPartitionForSecurityParent(currentSecParentExtId);
                if (partition != null) {
                    if (metadata == null) {
                        metadata = new Metadata();
                    }
                    Dimension partDim = new Dimension(PARTITION_DIMENSION_ID, "partition", true);
                    partDim.addEntities(new Entity(partition, partition));
                    metadata.replaceDimension(partDim);
                    builder.aspect(METADATA_ASPECT_NAME, metadata);
                    modified = true;
                }
            }

            return modified ? builder.build() : input;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in SecParentPreStoreHook", e);
            return input;
        }
    }

    private String getPartitionFromMetadata(Metadata metadata) {
        if (metadata == null) return null;
        Dimension dim = metadata.getDimensionById(PARTITION_DIMENSION_ID);
        if (dim == null || dim.getEntities() == null || dim.getEntities().isEmpty()) return null;
        return dim.getEntities().get(0).getId();
    }

    private String getSecParentExternalId(InsertionInfoAspectBean ii, ContentManager cm, Subject subject) {
        if (ii == null || ii.getSecurityParentId() == null) return null;
        try {
            ContentId secParentId = ii.getSecurityParentId();
            // The security parent external ID is typically used for mapping
            // For now return the ID string representation
            return IdUtil.toIdString(secParentId);
        } catch (Exception e) {
            return null;
        }
    }
}
