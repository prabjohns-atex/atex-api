package com.atex.plugins.structured.text;

import java.util.Objects;

/**
 * Bean class representing a single note in a text.
 */
public class Note {
    private String text;
    private String user;
    private String created;
    private String modified;

    public Note(String text, String user, String created, String modified) {
        this.text = text;
        this.user = user;
        this.created = created;
        this.modified = modified;
    }

    public Note() {
        this(null, null, null, null);
    }

    public String getText() { return text; }
    public void setText(final String text) { this.text = text; }
    public String getUser() { return user; }
    public void setUser(final String user) { this.user = user; }
    public String getCreated() { return created; }
    public void setCreated(final String created) { this.created = created; }
    public String getModified() { return modified; }
    public void setModified(final String modified) { this.modified = modified; }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof Note n) {
            return Objects.equals(n.text, this.text)
                    && Objects.equals(n.user, this.user)
                    && Objects.equals(n.created, this.created)
                    && Objects.equals(n.modified, this.modified);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, user, created, modified);
    }
}

