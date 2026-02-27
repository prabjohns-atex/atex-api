package com.atex.onecms.content;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Identity and credentials carrier.
 */
public class Subject implements Serializable {
    public static final Subject NOBODY_CALLER = new Subject("nobody", "");

    private final String principal;
    private final String publicCredentials;

    public Subject(final String principal, final String publicCredentials) {
        this.principal = principal;
        this.publicCredentials = publicCredentials;
    }

    public Subject(final Subject subject) {
        if (subject != null) {
            this.principal = subject.getPrincipalId();
            this.publicCredentials = subject.getPublicCredentials();
        } else {
            this.principal = null;
            this.publicCredentials = null;
        }
    }

    public static Subject of(final String principal) {
        return new Subject(principal, null);
    }

    public static Subject of(final String principal, final String publicCredentials) {
        return new Subject(principal, publicCredentials);
    }

    public String getPrincipalId() {
        return principal;
    }

    public String getPublicCredentials() {
        return publicCredentials;
    }

    public boolean same(final String principal) {
        return Objects.equals(getPrincipalId(), principal);
    }

    public boolean same(final Subject subject) {
        return same(Optional.ofNullable(subject)
                            .map(Subject::getPrincipalId)
                            .orElse(null));
    }
}
