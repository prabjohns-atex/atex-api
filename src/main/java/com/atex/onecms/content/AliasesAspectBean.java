package com.atex.onecms.content;

import java.util.HashMap;
import java.util.Map;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

/**
 * Aspect for named content aliases (e.g. externalId).
 * Ported from polopoly core/data-api-internal.
 */
@AspectDefinition(AliasesAspectBean.ASPECT_NAME)
public class AliasesAspectBean {
    public static final String ASPECT_NAME = "atex.Aliases";

    private Map<String, String> aliases;

    public AliasesAspectBean() {
        this(new HashMap<>());
    }

    public AliasesAspectBean(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    public Map<String, String> getAliases() { return aliases; }
    public void setAliases(Map<String, String> aliases) { this.aliases = aliases; }
}
