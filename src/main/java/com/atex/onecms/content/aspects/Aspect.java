package com.atex.onecms.content.aspects;

import com.atex.onecms.content.ContentVersionId;

/**
 * Represents an aspect with data stored in a Java object of type T.
 */
public class Aspect<T> {
    private String name;
    private T data;
    private ContentVersionId version;

    public Aspect() {}

    public Aspect(String name, T data) {
        this.name = name;
        this.data = data;
        this.version = null;
    }

    public Aspect(String name, T data, ContentVersionId version) {
        this.name = name;
        this.data = data;
        this.version = version;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ContentVersionId getVersion() { return version; }
    public void setVersion(ContentVersionId version) { this.version = version; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Aspect<?> aspect = (Aspect<?>) o;
        if (data != null ? !data.equals(aspect.data) : aspect.data != null) return false;
        if (name != null ? !name.equals(aspect.name) : aspect.name != null) return false;
        if (version != null ? !version.equals(aspect.version) : aspect.version != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (data != null ? data.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Aspect{name='" + name + "', data=" + data + ", version=" + version + '}';
    }
}
