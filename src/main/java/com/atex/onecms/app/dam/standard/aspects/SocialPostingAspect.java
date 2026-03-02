package com.atex.onecms.app.dam.standard.aspects;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.atex.onecms.app.dam.types.SocialPostingAccount;
import com.atex.onecms.app.dam.types.SocialPostingPlatform;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(SocialPostingAspect.ASPECT_NAME)
public class SocialPostingAspect implements PostPublishUpdate {

    public static final String ASPECT_NAME = "atex.SocialPosting";

    private Map<String, SocialPostingPlatform> platforms;
    private List<SocialPostingAccount> accounts;

    public Map<String, SocialPostingPlatform> getPlatforms() { return platforms; }
    public void setPlatforms(final Map<String, SocialPostingPlatform> platforms) { this.platforms = platforms; }
    public List<SocialPostingAccount> getAccounts() { return accounts; }
    public void setAccounts(final List<SocialPostingAccount> accounts) { this.accounts = accounts; }

    @Override
    public boolean postPublishUpdate() {
        final boolean isEmpty = Optional
            .ofNullable(getAccounts())
            .map(List::isEmpty)
            .orElse(true);
        if (!isEmpty) {
            setAccounts(null);
            return true;
        }
        return false;
    }
}

