package com.atex.desk.api.service;

/**
 * Thrown when an alias already points to a different content item.
 */
public class AliasConflictException extends RuntimeException
{
    private final String aliasName;
    private final String aliasValue;
    private final String conflictingContentId;

    public AliasConflictException(String aliasName, String aliasValue, String conflictingContentId)
    {
        super("Alias '" + aliasName + "' with value '" + aliasValue
              + "' already assigned to content: " + conflictingContentId);
        this.aliasName = aliasName;
        this.aliasValue = aliasValue;
        this.conflictingContentId = conflictingContentId;
    }

    public String getAliasName() { return aliasName; }
    public String getAliasValue() { return aliasValue; }
    public String getConflictingContentId() { return conflictingContentId; }
}
