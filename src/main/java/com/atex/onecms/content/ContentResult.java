package com.atex.onecms.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.atex.onecms.content.aspects.Aspect;

/**
 * Contains operation result and the content, if successful.
 */
public class ContentResult<T> extends Result<Content<T>> {
    private final Meta meta;
    private final ContentVersionId contentId;

    ContentResult(Content<T> content, ContentVersionId id, Meta meta) {
        this(Status.OK, content, id, meta);
    }

    ContentResult(Status status, Content<T> content, ContentVersionId id, Meta meta) {
        super(status, content);
        this.contentId = id;
        this.meta = meta;
    }

    ContentResult(Status status, Content<T> content, Meta meta) {
        this(status, content, content != null ? content.getId() : null, meta);
    }

    ContentResult(Status status, ContentVersionId contentId) {
        super(status);
        this.contentId = contentId;
        this.meta = null;
    }

    ContentResult(final ContentVersionId id, final String type, final Aspect<T> contentData,
                  final String variant, final Meta meta, final Collection<Aspect> aspects) {
        this(new Content<>(type, contentData, id, variant, aspects), id, meta);
    }

    ContentResult(final ContentVersionId id, final String type, final Aspect<T> contentData,
                  final String variant, final Meta meta) {
        this(id, type, contentData, variant, meta, new ArrayList<>());
    }

    /**
     * Copy constructor replacing main aspect.
     */
    public ContentResult(ContentResult<?> originalResult, Aspect<T> mainAspect,
                         Collection<Aspect> aspects) {
        this(new Content<>(originalResult.getContent().getContentDataType(),
                           mainAspect,
                           originalResult.getContent().getId(),
                           originalResult.getContent().getVariant(),
                           aspects),
                originalResult.getContent().getId(),
                originalResult.getMeta());
    }

    public Content<T> getContent() {
        return super.getData();
    }

    public Meta getMeta() {
        return meta;
    }

    public ContentVersionId getContentId() {
        return contentId;
    }

    // Static factory methods delegating to ContentResultBuilder

    public static <T> ContentResultBuilder<T> mainAspectData(final T mainAspectData) {
        return new ContentResultBuilder<T>().mainAspectData(mainAspectData);
    }

    public static <T> ContentResultBuilder<T> mainAspect(final Aspect<T> mainAspect) {
        return new ContentResultBuilder<T>().mainAspect(mainAspect);
    }

    public static <T> ContentResultBuilder<T> id(ContentVersionId id) {
        return new ContentResultBuilder<T>().id(id);
    }

    public static <T> ContentResultBuilder<T> aspects(Collection<Aspect> aspects) {
        return new ContentResultBuilder<T>().aspects(aspects);
    }

    public static <T> ContentResultBuilder<T> meta(Meta meta) {
        return new ContentResultBuilder<T>().meta(meta);
    }

    public static <T> ContentResultBuilder<T> variant(String variant) {
        return new ContentResultBuilder<T>().variant(variant);
    }

    public static <T> ContentResultBuilder<T> content(Content<T> content) {
        return new ContentResultBuilder<T>().content(content);
    }

    public static <T> ContentResultBuilder<T> status(Status status) {
        return new ContentResultBuilder<T>().status(status);
    }

    public static <T> ContentResultBuilder<T> type(String type) {
        return new ContentResultBuilder<T>().type(type);
    }

    public static <T> ContentResultBuilder<T> copy(ContentResult<T> contentResult) {
        return ContentResultBuilder.copy(contentResult);
    }

    public static <IN, T> ContentResultBuilder<T> copyWithMainAspect(ContentResult<IN> contentResult,
                                                                      T mainAspectData) {
        return ContentResultBuilder.copyWithMainAspect(contentResult, mainAspectData);
    }

    public static <IN, T> ContentResultBuilder<T> copyWithMainAspect(ContentResult<IN> contentResult,
                                                                      Aspect<T> mainAspect) {
        return ContentResultBuilder.copyWithMainAspect(contentResult, mainAspect);
    }

    public static <IN, T> ContentResultBuilder<T> copyWithMainAspect(ContentResult<IN> contentResult,
                                                                      T mainAspectData, String type) {
        return ContentResultBuilder.copyWithMainAspect(contentResult, mainAspectData, type);
    }

    public static <T> ContentResult<T> of(final ContentVersionId contentId, final Status status) {
        Objects.requireNonNull(status, "status");
        return new ContentResultBuilder<T>().id(contentId).status(status).build();
    }

    public static <T> ContentResult<T> failed(final ContentVersionId contentId, final Status status) {
        Objects.requireNonNull(status, "status");
        if (status.isSuccess()) {
            throw new IllegalArgumentException("status should be a failure");
        }
        return of(contentId, status);
    }

    @Override
    public String toString() {
        return "ContentResult{meta=" + meta + ", contentId=" + contentId +
               ", content=" + getData() + ", status=" + getStatus() + '}';
    }

    /**
     * Content version information.
     */
    public static class Meta {
        private final long modificationTime;
        private final long originalCreationTime;

        public Meta(long modificationTime, long originalCreationTime) {
            this.modificationTime = modificationTime;
            this.originalCreationTime = originalCreationTime;
        }

        public long getModificationTime() { return modificationTime; }
        public long getOriginalCreationTime() { return originalCreationTime; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Meta meta = (Meta) o;
            return modificationTime == meta.modificationTime &&
                   originalCreationTime == meta.originalCreationTime;
        }

        @Override
        public int hashCode() {
            int result = (int) (modificationTime ^ (modificationTime >>> 32));
            result = 31 * result + (int) (originalCreationTime ^ (originalCreationTime >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "Meta{modificationTime=" + modificationTime +
                   ", originalCreationTime=" + originalCreationTime + '}';
        }
    }
}
