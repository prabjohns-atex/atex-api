package com.atex.onecms.content.repository;

/**
 * Thrown when an alias already exists.
 */
public class AliasConflictException extends StorageException {

    public AliasConflictException(String message) {
        super(message);
    }

    public AliasConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
