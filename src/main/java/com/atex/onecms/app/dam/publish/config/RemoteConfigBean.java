package com.atex.onecms.app.dam.publish.config;

import com.atex.onecms.app.dam.engagement.EngagementElement;
import java.util.List;

public class RemoteConfigBean {
    private String id;
    private String remoteApiUrl;
    private String remoteFileServiceUrl;
    private String remoteImageService;
    private String remoteUser;
    private String remotePassword;
    private String remoteModuleClassName;
    private String remoteModuleImporterClassName;
    private String remoteBeanMappingFilename;
    private String previewAdapterClassName;
    private String previewConfigClassName;
    private String publishConfigId;
    private String importConfigId;
    private String customPublishConfigId;
    private EngagementConfiguration engagement;
    private Object previewConfig;
    private String domainOverride;
    private String jsDomainOverride;

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getRemoteApiUrl() { return remoteApiUrl; }
    public void setRemoteApiUrl(String v) { this.remoteApiUrl = v; }
    public String getRemoteFileServiceUrl() { return remoteFileServiceUrl; }
    public void setRemoteFileServiceUrl(String v) { this.remoteFileServiceUrl = v; }
    public String getRemoteImageService() { return remoteImageService; }
    public void setRemoteImageService(String v) { this.remoteImageService = v; }
    public String getRemoteUser() { return remoteUser; }
    public void setRemoteUser(String v) { this.remoteUser = v; }
    public String getRemotePassword() { return remotePassword; }
    public void setRemotePassword(String v) { this.remotePassword = v; }
    public String getRemoteModuleClassName() { return remoteModuleClassName; }
    public void setRemoteModuleClassName(String v) { this.remoteModuleClassName = v; }
    public String getRemoteModuleImporterClassName() { return remoteModuleImporterClassName; }
    public void setRemoteModuleImporterClassName(String v) { this.remoteModuleImporterClassName = v; }
    public String getRemoteBeanMappingFilename() { return remoteBeanMappingFilename; }
    public void setRemoteBeanMappingFilename(String v) { this.remoteBeanMappingFilename = v; }
    public String getPreviewAdapterClassName() { return previewAdapterClassName; }
    public void setPreviewAdapterClassName(String v) { this.previewAdapterClassName = v; }
    public String getPreviewConfigClassName() { return previewConfigClassName; }
    public void setPreviewConfigClassName(String v) { this.previewConfigClassName = v; }
    public String getPublishConfigId() { return publishConfigId; }
    public void setPublishConfigId(String v) { this.publishConfigId = v; }
    public String getImportConfigId() { return importConfigId; }
    public void setImportConfigId(String v) { this.importConfigId = v; }
    public String getCustomPublishConfigId() { return customPublishConfigId; }
    public void setCustomPublishConfigId(String v) { this.customPublishConfigId = v; }
    public EngagementConfiguration getEngagement() { return engagement; }
    public void setEngagement(EngagementConfiguration v) { this.engagement = v; }
    public Object getPreviewConfig() { return previewConfig; }
    public void setPreviewConfig(Object v) { this.previewConfig = v; }
    public String getDomainOverride() { return domainOverride; }
    public void setDomainOverride(String v) { this.domainOverride = v; }
    public String getJsDomainOverride() { return jsDomainOverride; }
    public void setJsDomainOverride(String v) { this.jsDomainOverride = v; }

    public static class EngagementConfiguration {
        private String appType;
        private List<EngagementElement> attributes;
        public String getAppType() { return appType; }
        public void setAppType(String v) { this.appType = v; }
        public List<EngagementElement> getAttributes() { return attributes; }
        public void setAttributes(List<EngagementElement> v) { this.attributes = v; }
    }
}
