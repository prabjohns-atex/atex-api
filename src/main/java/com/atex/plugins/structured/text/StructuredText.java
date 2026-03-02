package com.atex.plugins.structured.text;

import java.util.Map;
import java.util.Objects;

/**
 * Bean class representing a text containing notes.
 */
public class StructuredText {
    private String text;
    private Map<String, Note> notes;

    public StructuredText(String text, Map<String, Note> notes) {
        this.text = text;
        this.notes = notes;
    }

    public StructuredText(String text) {
        this(text, null);
    }

    public StructuredText() {
        this(null);
    }

    public String getText() { return text; }
    public void setText(final String text) { this.text = text; }
    public Map<String, Note> getNotes() { return notes; }
    public void setNotes(final Map<String, Note> notes) { this.notes = notes; }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof StructuredText st) {
            return Objects.equals(st.text, this.text) && Objects.equals(st.notes, this.notes);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, notes);
    }

    @Override
    public String toString() {
        return text != null ? text : "";
    }
}

