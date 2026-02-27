package com.polopoly.user.server;

import java.io.Serializable;

/**
 * Primary key for Group objects.
 */
public class GroupId implements Serializable {
    private final int id;

    public GroupId(int id) {
        this.id = id;
    }

    public int getId() { return id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((GroupId) o).id;
    }

    @Override
    public int hashCode() { return id; }

    @Override
    public String toString() { return "group:" + id; }
}
