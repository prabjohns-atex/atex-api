package com.polopoly.cm.policy;

import com.polopoly.cm.ContentId;
import com.polopoly.cm.ExternalContentId;
import com.polopoly.cm.client.CMException;
import com.polopoly.user.server.Caller;

/**
 * PolicyCMServer interface - subset of methods used by DamDataResource.
 * Other methods throw UnsupportedOperationException.
 */
public interface PolicyCMServer {

    Policy getPolicy(ContentId contentId) throws CMException;

    ContentId findContentIdByExternalId(ExternalContentId externalId) throws CMException;

    Caller getCurrentCaller();

    void setCurrentCaller(Caller caller);

    boolean contentExists(ContentId contentId) throws CMException;
}
