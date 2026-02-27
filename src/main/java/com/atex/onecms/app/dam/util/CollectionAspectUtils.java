package com.atex.onecms.app.dam.util;

import com.atex.onecms.app.dam.collection.aspect.CollectionAspect;

import java.util.List;

public class CollectionAspectUtils {

    /**
     * Merge collection update into existing aspect.
     * Returns null if no changes were needed, or the updated aspect.
     */
    public static CollectionAspect update(CollectionAspect existing, CollectionAspect update) {
        if (existing == null) return update;
        if (update == null) return null;

        List<String> existingIds = existing.getContentIds();
        List<String> updateIds = update.getContentIds();

        if (existingIds == null && updateIds == null) {
            return null; // No changes
        }

        // If update has content IDs, merge them
        if (updateIds != null) {
            if (existingIds != null && existingIds.equals(updateIds)) {
                // Check if name changed
                if (existing.getName() != null && existing.getName().equals(update.getName())) {
                    return null; // No changes
                }
            }
            existing.setContentIds(updateIds);
        }

        // Update name if provided
        if (update.getName() != null) {
            existing.setName(update.getName());
        }

        return existing;
    }
}
