package com.atex.onecms.content;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atex.onecms.content.aspects.Aspect;

/**
 * An object representing a write operation on a content.
 */
public class ContentWrite<T> {
    private String contentDataType;
    private T contentData;
    private final Content<T> origin;
    private final Map<String, Aspect> aspects = new HashMap<>(2);
    private List<ContentOperation> operations = new ArrayList<>();

    public ContentWrite(Content<T> origin, ContentOperation... operations) {
        this.origin = origin;
        if (origin != null) {
            this.contentDataType = origin.getContentDataType();
        }
        this.contentData = null;
        this.operations = new ArrayList<>(Arrays.asList(operations));
    }

    public ContentWrite(String contentType, T content, ContentOperation... operations) {
        this.origin = new Content<>(contentType, null, (ContentVersionId) null);
        this.contentDataType = contentType;
        this.contentData = content;
        this.operations = new ArrayList<>(Arrays.asList(operations));
    }

    ContentWrite(final Content<T> origin, final String contentDataType, final T contentData,
                 final Collection<Aspect> aspects, final Collection<ContentOperation> operations) {
        this.origin = origin;
        this.contentDataType = contentDataType;
        this.contentData = contentData;
        this.operations = new ArrayList<>(operations);
        for (Aspect aspect : aspects) {
            this.aspects.put(aspect.getName(), aspect);
        }
    }

    public ContentWrite<T> addOperation(ContentOperation operation) {
        if (operation == null) throw new IllegalArgumentException("Null is not a valid command");
        operations.add(operation);
        return this;
    }

    public List<ContentOperation> getOperations() {
        return Collections.unmodifiableList(operations);
    }

    public String getVariant() {
        return origin != null ? origin.getVariant() : null;
    }

    public T getContentData() { return contentData; }
    public String getContentDataType() { return contentDataType; }

    public ContentWrite<T> setContentData(T contentData) {
        this.contentData = contentData;
        return this;
    }

    public ContentVersionId getId() {
        return origin != null ? origin.getId() : null;
    }

    public Object getAspect(String name) {
        Aspect<?> aspect = aspects.get(name);
        return (aspect == null) ? null : aspect.getData();
    }

    public <A> A getAspect(String name, Class<A> aspectClass) {
        return aspectClass.cast(getAspect(name));
    }

    public ContentWrite<T> setAspect(String name, Object dataBean) {
        aspects.put(name, new Aspect<>(name, dataBean));
        return this;
    }

    public Content<T> getOrigin() { return origin; }

    public Collection<Aspect> getAspects() {
        ArrayList<Aspect> result = new ArrayList<>();
        for (Aspect aspect : aspects.values()) {
            Aspect originAspect = origin != null ? origin.getAspect(aspect.getName()) : null;
            result.add(new Aspect<>(aspect.getName(), aspect.getData(),
                                    originAspect == null ? null : originAspect.getVersion()));
        }
        return Collections.unmodifiableCollection(result);
    }

    // Builder statics

    public static <T> ContentWriteBuilder<T> origin(Content<T> content) {
        return new ContentWriteBuilder<T>().origin(content);
    }

    public static <T> ContentWriteBuilder<T> origin(ContentVersionId version) {
        return new ContentWriteBuilder<T>().origin(version);
    }

    public static <T> ContentWriteBuilder<T> aspect(String name, Object data) {
        return new ContentWriteBuilder<T>().aspect(name, data);
    }

    public static <T> ContentWriteBuilder<T> aspect(final Aspect aspect) {
        return new ContentWriteBuilder<T>().aspect(aspect);
    }

    public static <T> ContentWriteBuilder<T> aspects(Aspect... aspects) {
        return new ContentWriteBuilder<T>().aspects(aspects);
    }

    public static <T> ContentWriteBuilder<T> aspects(Collection<Aspect> aspects) {
        return new ContentWriteBuilder<T>().aspects(aspects);
    }

    public static <T> ContentWriteBuilder<T> mainAspectData(T contentData) {
        return new ContentWriteBuilder<T>().mainAspectData(contentData);
    }

    public static <T> ContentWriteBuilder<T> mainAspect(Aspect<T> aspect) {
        return new ContentWriteBuilder<T>().mainAspect(aspect);
    }

    public static <T> ContentWriteBuilder<T> type(String typeName) {
        return new ContentWriteBuilder<T>().type(typeName);
    }

    public static <T> ContentWriteBuilder<T> operation(ContentOperation operation) {
        return new ContentWriteBuilder<T>().operations(operation);
    }

    public static <T> ContentWriteBuilder<T> operations(ContentOperation... operations) {
        return new ContentWriteBuilder<T>().operations(operations);
    }

    public static <T> ContentWriteBuilder<T> operations(Collection<ContentOperation> operations) {
        return new ContentWriteBuilder<T>().operations(operations);
    }
}
