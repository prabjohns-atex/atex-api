package com.atex.onecms.content;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.atex.onecms.content.aspects.Aspect;

/**
 * Builder for ContentResult instances.
 */
public final class ContentResultBuilder<T> {
    private ContentVersionId id;
    private ContentResult.Meta meta = null;
    private Status status = Status.OK;
    private final Map<String, Aspect> aspects = new HashMap<>();
    private String type;
    private T mainAspectData;
    private ContentVersionId mainAspectId;
    private String variant;

    public ContentResultBuilder() {}

    public static <T> ContentResultBuilder<T> copy(final ContentResult<T> contentResult) {
        if (contentResult.getContent() != null) {
            return copyWithMainAspect(contentResult,
                                      contentResult.getContent().getContentData(),
                                      contentResult.getContent().getContentDataType());
        } else {
            return copyWithMainAspect(contentResult, null, null);
        }
    }

    public static <IN, T> ContentResultBuilder<T> copyWithMainAspect(
            final ContentResult<IN> contentResult, final T mainAspectData) {
        return copyWithMainAspect(contentResult, mainAspectData, null);
    }

    public static <IN, T> ContentResultBuilder<T> copyWithMainAspect(
            final ContentResult<IN> contentResult, final Aspect<T> mainAspect) {
        if (mainAspect != null) {
            return copyWithMainAspect(contentResult, mainAspect.getData(), mainAspect.getName());
        } else {
            return copyWithMainAspect(contentResult, null, null);
        }
    }

    public static <IN, T> ContentResultBuilder<T> copyWithMainAspect(
            final ContentResult<IN> contentResult, final T mainAspectData, final String type) {
        final ContentResultBuilder<T> builder = new ContentResultBuilder<>();
        builder.id(contentResult.getContentId());
        builder.meta(contentResult.getMeta());
        builder.status(contentResult.getStatus());
        ContentVersionId mainAspectId = null;

        final String dataType;
        Content<?> content = contentResult.getContent();
        if (content != null) {
            builder.aspects(content.getAspects());
            builder.variant(content.getVariant());
            dataType = Optional.ofNullable(type).orElse(content.getContentDataType());
            if (content.getContentAspect() != null) {
                mainAspectId = content.getContentAspect().getVersion();
            }
        } else {
            dataType = type;
        }
        builder.mainAspect(new Aspect<>(dataType, mainAspectData, mainAspectId));
        return builder;
    }

    public static <IN, T> ContentResultBuilder<T> copyWithAspects(
            final ContentResult<IN> contentResult, final T mainAspectData,
            final String type, final Collection<Aspect> aspects) {
        return copyWithMainAspect(contentResult, mainAspectData, type)
            .clearAspects()
            .aspects(aspects);
    }

    public static <IN, T> ContentResultBuilder<T> copyWithAspects(
            final ContentResult<IN> contentResult, final T mainAspectData,
            final Collection<Aspect> aspects) {
        return copyWithAspects(contentResult, mainAspectData, null, aspects);
    }

    public ContentResultBuilder<T> content(final Content<T> content) {
        this.id(content.getId());
        this.aspects(content.getAspects());
        this.type(content.getContentDataType());
        this.mainAspectData(content.getContentData());
        this.variant(content.getVariant());
        return this;
    }

    public ContentResultBuilder<T> id(final ContentVersionId id) {
        this.id = id;
        return this;
    }

    public ContentResultBuilder<T> meta(final ContentResult.Meta meta) {
        this.meta = meta;
        return this;
    }

    public ContentResultBuilder<T> status(final Status status) {
        this.status = status;
        return this;
    }

    public ContentResultBuilder<T> aspects(final Aspect... aspects) {
        for (Aspect aspect : aspects) {
            this.aspects.put(aspect.getName(), aspect);
        }
        return this;
    }

    public ContentResultBuilder<T> aspects(final Collection<Aspect> aspects) {
        if (aspects != null) {
            for (final Aspect<?> aspect : aspects) {
                this.aspects.put(aspect.getName(), aspect);
            }
        }
        return this;
    }

    public ContentResultBuilder<T> removeAspect(final String aspectName) {
        this.aspects.remove(aspectName);
        return this;
    }

    public ContentResultBuilder<T> clearAspects() {
        this.aspects.clear();
        return this;
    }

    public ContentResultBuilder<T> type(final String type) {
        this.type = type;
        return this;
    }

    public ContentResultBuilder<T> variant(final String variant) {
        this.variant = variant;
        return this;
    }

    public ContentResultBuilder<T> mainAspectData(final T mainAspectData) {
        this.mainAspectData = mainAspectData;
        return this;
    }

    public ContentResultBuilder<T> mainAspect(final Aspect<T> mainAspect) {
        if (mainAspect != null) {
            this.mainAspectData(mainAspect.getData());
            this.type(mainAspect.getName());
            this.mainAspectId = mainAspect.getVersion();
        } else {
            this.mainAspectData(null);
            this.type(null);
            this.mainAspectId = null;
        }
        return this;
    }

    public ContentResult<T> build() {
        status = status != null ? status : Status.OK;
        if (Status.OK.equals(status) || Status.CREATED.equals(status)) {
            final String resolvedType;
            if (type != null) {
                resolvedType = type;
            } else if (mainAspectData != null) {
                resolvedType = mainAspectData.getClass().getName();
            } else {
                resolvedType = null;
            }
            final Aspect<T> mainAspect = Optional.ofNullable(mainAspectData)
                .map(d -> new Aspect<>(resolvedType, d, mainAspectId))
                .orElse(null);
            Content<T> content = new Content<>(resolvedType, mainAspect, id, variant, aspects.values());
            return new ContentResult<>(status, content, meta);
        } else {
            return new ContentResult<>(status, id);
        }
    }
}
