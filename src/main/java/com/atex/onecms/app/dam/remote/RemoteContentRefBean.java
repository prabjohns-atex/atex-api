package com.atex.onecms.app.dam.remote;

import com.atex.onecms.content.ContentId;

public class RemoteContentRefBean {
    private String remoteId;
    private String remoteUrl;
    private String remoteName;
    private ContentId contentId;
    public String getRemoteId() { return remoteId; }
    public void setRemoteId(String v) { this.remoteId = v; }
    public String getRemoteUrl() { return remoteUrl; }
    public void setRemoteUrl(String v) { this.remoteUrl = v; }
    public String getRemoteName() { return remoteName; }
    public void setRemoteName(String v) { this.remoteName = v; }
    public ContentId getContentId() { return contentId; }
    public void setContentId(ContentId v) { this.contentId = v; }
}
