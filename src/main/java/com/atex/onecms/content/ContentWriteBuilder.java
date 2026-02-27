package com.atex.onecms.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.atex.onecms.content.aspects.Aspect;

/**
 * Builder for ContentWrite instances.
 */
public final class ContentWriteBuilder<T> {
    private Content<T> origin;
    private T mainAspectData;
    private final Map<String, Aspect> aspects = new HashMap<>();
    private String mainAspectType;
    private final List<ContentOperation> operations = new ArrayList<>();

    public ContentWriteBuilder() {}

    public static <S> ContentWriteBuilder<S> from(ContentWrite<S> contentWrite) {
        Objects.requireNonNull(contentWrite);
        ContentWriteBuilder<S> builder = new ContentWriteBuilder<>();
        builder.origin(contentWrite.getOrigin());
        if (contentWrite.getOperations() != null) {
            builder.operations(contentWrite.getOperations());
        }
        builder.mainAspectData(contentWrite.getContentData());
        builder.type(contentWrite.getContentDataType());
        builder.aspects(contentWrite.getAspects());
        return builder;
    }

    public static <S> ContentWriteBuilder<S> from(final ContentResult<S> contentResult) {
        Objects.requireNonNull(contentResult);
        final ContentWriteBuilder<S> cw = new ContentWriteBuilder<S>()
            .origin(contentResult.getContentId());
        final Content<S> content = contentResult.getContent();
        if (content != null) {
            return cw.type(content.getContentDataType())
                   .mainAspectData(content.getContentData())
                   .aspects(content.getAspects());
        }
        return cw;
    }

    public ContentWriteBuilder<T> origin(Content<T> content) {
        this.origin = content;
        return this;
    }

    public ContentWriteBuilder<T> origin(ContentVersionId contentId) {
        this.origin = new Content<>(null, null, contentId);
        return this;
    }

    public ContentWriteBuilder<T> aspect(String name, Object data) {
        if (data == null) throw new IllegalArgumentException("Aspect data must be non-null for " + name);
        aspects.put(name, new Aspect<>(name, data));
        return this;
    }

    public <A> ContentWriteBuilder<T> aspect(final Aspect<A> aspect) {
        aspects.put(aspect.getName(), aspect);
        return this;
    }

    public ContentWriteBuilder<T> aspects(Aspect... aspects) {
        for (Aspect aspect : aspects) {
            this.aspects.put(aspect.getName(), aspect);
        }
        return this;
    }

    public ContentWriteBuilder<T> aspects(Collection<Aspect> aspects) {
        for (Aspect aspect : aspects) {
            this.aspects.put(aspect.getName(), aspect);
        }
        return this;
    }

    public ContentWriteBuilder<T> removeAspect(String aspectName) {
        aspects.remove(aspectName);
        return this;
    }

    public ContentWriteBuilder<T> filterAspects(final Predicate<Aspect<?>> filter) {
        Objects.requireNonNull(filter);
        final Map<String, Aspect<?>> newAspects = new HashMap<>();
        aspects.entrySet().stream()
               .filter(e -> filter.test(e.getValue()))
               .forEach(e -> newAspects.put(e.getKey(), e.getValue()));
        aspects.clear();
        aspects.putAll(newAspects);
        return this;
    }

    public ContentWriteBuilder<T> clearAspects() {
        aspects.clear();
        return this;
    }

    public ContentWriteBuilder<T> mainAspectData(T mainAspectData) {
        this.mainAspectData = mainAspectData;
        return this;
    }

    public ContentWriteBuilder<T> mainAspect(Aspect<T> mainAspect) {
        mainAspectData(mainAspect.getData());
        type(mainAspect.getName());
        return this;
    }

    public ContentWriteBuilder<T> type(String typeName) {
        this.mainAspectType = typeName;
        return this;
    }

    public ContentWriteBuilder<T> operation(ContentOperation operation) {
        this.operations.add(operation);
        return this;
    }

    public ContentWriteBuilder<T> operations(ContentOperation... operations) {
        Collections.addAll(this.operations, operations);
        return this;
    }

    public ContentWriteBuilder<T> operations(Collection<ContentOperation> operations) {
        this.operations.addAll(operations);
        return this;
    }

    public ContentWriteBuilder<T> clearOperations() {
        this.operations.clear();
        return this;
    }

    public ContentWriteBuilder<T> filterOperations(final Predicate<ContentOperation> filter) {
        Objects.requireNonNull(filter);
        final List<ContentOperation> newOps = this.operations.stream()
            .filter(filter).collect(Collectors.toList());
        this.operations.clear();
        this.operations.addAll(newOps);
        return this;
    }

    public ContentWrite<T> buildUpdate() {
        if (origin == null) {
            throw new IllegalStateException("Content can't be updated without an origin version");
        }
        ContentWrite<T> update = build();
        if (update.getContentData() != null && update.getContentDataType() == null) {
            throw new IllegalStateException("Can't update content without a content type");
        }
        return update;
    }

    public ContentWrite<T> build() {
        String resolvedType = mainAspectType;
        if (resolvedType == null && origin != null) {
            resolvedType = origin.getContentDataType();
        }
        if (resolvedType == null && mainAspectData != null) {
            resolvedType = mainAspectData.getClass().getName();
        }
        return new ContentWrite<>(origin, resolvedType, mainAspectData, aspects.values(), operations);
    }

    public ContentWrite<T> buildCreate() {
        if (mainAspectData == null) {
            throw new IllegalStateException("Content can't be created without main aspect data.");
        }
        ContentWrite<T> create = build();
        if (create.getContentDataType() == null) {
            throw new IllegalStateException("Content can't be created without a content type.");
        }
        return create;
    }

    public ContentWrite<T> buildWorkspaceCreate() {
        return buildCreate();
    }

    public ContentWrite<T> buildWorkspaceUpdate() {
        if (mainAspectData == null && aspects.isEmpty()) {
            throw new IllegalStateException("Can't update content without any aspect data");
        }
        if (!operations.isEmpty()) {
            throw new IllegalStateException("Operations are not supported on workspace updates");
        }
        return build();
    }
}
