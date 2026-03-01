package com.atex.onecms.search;

public class SearchOptions {

    public enum ACCESS_PERMISSION {
        OFF, READ, WRITE, OWNER;

        public static ACCESS_PERMISSION from(final String value) {
            if (value == null || value.isEmpty()) {
                return OFF;
            }
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return OFF;
            }
        }
    }

    private boolean filterWorkingSites;
    private boolean postMethod;
    private ACCESS_PERMISSION permission = ACCESS_PERMISSION.OFF;

    private SearchOptions() {
    }

    public boolean isFilterWorkingSites() {
        return filterWorkingSites;
    }

    public boolean isPostMethod() {
        return postMethod;
    }

    public ACCESS_PERMISSION getPermission() {
        return permission;
    }

    public SearchOptions filterWorkingSites(final boolean filter) {
        this.filterWorkingSites = filter;
        return this;
    }

    public SearchOptions postMethod(final boolean post) {
        this.postMethod = post;
        return this;
    }

    public SearchOptions permission(final ACCESS_PERMISSION perm) {
        this.permission = perm != null ? perm : ACCESS_PERMISSION.OFF;
        return this;
    }

    public static SearchOptions none() {
        return new SearchOptions();
    }

    public static SearchOptions filterWorkingSites() {
        return new SearchOptions().filterWorkingSites(true);
    }

    public static SearchOptions withPermission(final ACCESS_PERMISSION permission) {
        return new SearchOptions().permission(permission);
    }

    public static SearchOptions postMethod() {
        return new SearchOptions().postMethod(true);
    }
}
