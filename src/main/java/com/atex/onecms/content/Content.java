package com.atex.onecms.content;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.atex.onecms.content.aspects.Aspect;

/**
 * Contains content data, variant and version information.
 */
public class Content<T> {
    private final String type;
    private final String variant;
    private final ContentVersionId id;
    private final Aspect<T> mainAspect;
    private final Map<String, Aspect> aspects;

    public Content(String type, Aspect<T> mainAspect, ContentVersionId id) {
        this(type, mainAspect, id, null);
    }

    public Content(String type, Aspect<T> mainAspect, ContentVersionId id, String variant) {
        this(type, mainAspect, id, variant, Collections.emptyList());
    }

    public Content(String type, Aspect<T> mainAspect, ContentVersionId id,
                   String variant, Collection<Aspect> aspects) {
        this.type = type;
        this.variant = variant;
        this.mainAspect = mainAspect;
        this.aspects = aspectMap(aspects);
        this.id = id;
    }

    private static Map<String, Aspect> aspectMap(Collection<Aspect> aspects) {
        HashMap<String, Aspect> map = new HashMap<>();
        if (aspects == null) return map;
        for (Aspect aspect : aspects) {
            map.put(aspect.getName(), aspect);
        }
        return map;
    }

    public String getVariant() { return variant; }

    public T getContentData() {
        return mainAspect == null ? null : mainAspect.getData();
    }

    public String getContentDataType() { return type; }

    public Collection<String> getAspectNames() { return aspects.keySet(); }

    public Aspect<T> getContentAspect() { return mainAspect; }

    @SuppressWarnings("unchecked")
    public <A> Aspect<A> getAspect(String name) {
        return (Aspect<A>) aspects.get(name);
    }

    @SuppressWarnings("unchecked")
    public <A> A getAspectData(String name) {
        Aspect aspect = aspects.get(name);
        return aspect == null ? null : (A) aspect.getData();
    }

    public ContentVersionId getId() { return id; }

    public Collection<Aspect> getAspects() {
        return Collections.unmodifiableCollection(aspects.values());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Content<?> content = (Content<?>) o;
        if (aspects != null ? !aspects.equals(content.aspects) : content.aspects != null) return false;
        if (id != null ? !id.equals(content.id) : content.id != null) return false;
        if (mainAspect != null ? !mainAspect.equals(content.mainAspect) : content.mainAspect != null) return false;
        if (type != null ? !type.equals(content.type) : content.type != null) return false;
        if (variant != null ? !variant.equals(content.variant) : content.variant != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (variant != null ? variant.hashCode() : 0);
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (mainAspect != null ? mainAspect.hashCode() : 0);
        result = 31 * result + (aspects != null ? aspects.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Content{type='" + type + "', variant='" + variant + "', id=" + id +
               ", mainAspect=" + mainAspect + ", aspects=" + aspects + '}';
    }
}
