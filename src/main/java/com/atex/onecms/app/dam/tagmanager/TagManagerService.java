package com.atex.onecms.app.dam.tagmanager;

import com.atex.common.collections.Pair;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.Status;
import com.atex.onecms.content.Subject;
import com.polopoly.metadata.Entity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Tag taxonomy management service.
 * Stub implementation — returns empty results.
 */
public class TagManagerService {

    private final ContentManager contentManager;

    public TagManagerService(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    public List<Entity> lookupTag(String dimensionId, String name, String parentId,
                                   int start, Subject subject) {
        return Collections.emptyList();
    }

    public Optional<Entity> searchTag(String dimensionId, String name, String parentId,
                                       Subject subject) {
        return Optional.empty();
    }

    public Pair<ContentId, Status> createTag(String dimensionId, String name, String parentId,
                                              String securityParentId, Subject subject) {
        return Pair.of(null, Status.NOT_FOUND);
    }

    public Pair<Entity, Status> tagToEntity(ContentId contentId, Subject subject) {
        return Pair.of(null, Status.NOT_FOUND);
    }
}

