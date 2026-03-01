package com.atex.onecms.app.dam.ayrshare;

import com.atex.onecms.content.ContentManager;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * AyrShare social media integration service.
 * Stub implementation — returns empty accounts.
 */
public class AyrShareService {

    private final ContentManager contentManager;

    public AyrShareService(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    public SocialAccountResponse getActiveAccounts() throws IOException {
        return new SocialAccountResponse();
    }

    public static class SocialAccountResponse {
        private List<SocialAccount> accounts = Collections.emptyList();
        public List<SocialAccount> getAccounts() { return accounts; }
        public void setAccounts(List<SocialAccount> accounts) { this.accounts = accounts; }
    }

    public static class SocialAccount {
        private String platform;
        private String displayName;
        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}

