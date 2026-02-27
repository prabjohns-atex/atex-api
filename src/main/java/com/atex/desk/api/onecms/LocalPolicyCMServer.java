package com.atex.desk.api.onecms;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.polopoly.cm.ExternalContentId;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.Policy;
import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.user.server.Caller;
import org.springframework.stereotype.Component;

/**
 * Local PolicyCMServer implementation that delegates to ContentManager.
 * Implements only the methods DamDataResource actually calls.
 */
@Component
public class LocalPolicyCMServer implements PolicyCMServer {

    private final ContentManager contentManager;
    private final CallerContext callerContext;

    public LocalPolicyCMServer(ContentManager contentManager, CallerContext callerContext) {
        this.contentManager = contentManager;
        this.callerContext = callerContext;
    }

    @Override
    public Policy getPolicy(com.polopoly.cm.ContentId contentId) throws CMException {
        // For external IDs, resolve first
        if (contentId instanceof ExternalContentId ext) {
            try {
                ContentVersionId resolved = contentManager.resolve(
                    ext.getExternalId(), Subject.NOBODY_CALLER);
                if (resolved == null) {
                    throw new CMException("Content not found: " + ext.getExternalId());
                }
                return new Policy(contentId);
            } catch (CMException e) {
                throw e;
            } catch (Exception e) {
                throw new CMException("Failed to get policy: " + contentId, e);
            }
        }
        return new Policy(contentId);
    }

    @Override
    public com.polopoly.cm.ContentId findContentIdByExternalId(ExternalContentId externalId)
            throws CMException {
        try {
            ContentVersionId resolved = contentManager.resolve(
                externalId.getExternalId(), Subject.NOBODY_CALLER);
            if (resolved == null) {
                throw new CMException("External ID not found: " + externalId.getExternalId());
            }
            return externalId;
        } catch (CMException e) {
            throw e;
        } catch (Exception e) {
            throw new CMException("Failed to find by external ID: " + externalId, e);
        }
    }

    @Override
    public Caller getCurrentCaller() {
        String principal = callerContext.getPrincipalId();
        return principal != null ? new Caller(principal) : Caller.NOBODY_CALLER;
    }

    @Override
    public void setCurrentCaller(Caller caller) {
        callerContext.setPrincipalId(caller != null ? caller.getLoginName() : null);
    }

    @Override
    public boolean contentExists(com.polopoly.cm.ContentId contentId) throws CMException {
        if (contentId instanceof ExternalContentId ext) {
            try {
                ContentVersionId resolved = contentManager.resolve(
                    ext.getExternalId(), Subject.NOBODY_CALLER);
                return resolved != null;
            } catch (Exception e) {
                return false;
            }
        }
        // For numeric IDs, convert to OneCMS ContentId and resolve
        try {
            ContentId onecmsId = new ContentId("policy",
                contentId.getMajor() + "." + contentId.getMinor());
            ContentVersionId resolved = contentManager.resolve(onecmsId, Subject.NOBODY_CALLER);
            return resolved != null;
        } catch (Exception e) {
            return false;
        }
    }
}
