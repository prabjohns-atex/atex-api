package com.atex.onecms.content;

/**
 * An operation that sets an alias for the content.
 */
public class SetAliasOperation implements ContentOperation {
    public static String EXTERNAL_ID = "externalId";

    private String namespace;
    private String alias;

    public SetAliasOperation() {
    }

    public SetAliasOperation(String namespace, String alias) {
        this.namespace = namespace;
        this.alias = alias;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}
