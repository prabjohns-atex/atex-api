package com.atex.desk.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the file service.
 *
 * Backend types:
 * - "local" (default): filesystem-backed storage
 * - "s3": Amazon S3 storage
 * - "hybrid": writes locally first, can serve from both local and S3
 */
@ConfigurationProperties(prefix = "desk.file-service")
public class FileServiceProperties {

    private String baseDir = "./files";
    private String backend = "local";

    // S3 configuration
    private S3Properties s3 = new S3Properties();

    public String getBaseDir() { return baseDir; }
    public void setBaseDir(String baseDir) { this.baseDir = baseDir; }

    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }

    public S3Properties getS3() { return s3; }
    public void setS3(S3Properties s3) { this.s3 = s3; }

    public static class S3Properties {

        private boolean enabled = false;
        private String region = "eu-west-1";
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucketContent = "content";
        private String bucketTmp = "tmp";
        private boolean oneBucketMode = false;
        private String contentPrefix = "content";
        private String tmpPrefix = "tmp";
        private boolean hybridMode = false;
        private boolean handlesContent = true;
        private boolean handlesTmp = true;
        private int maxConcurrentUploads = 10;
        private long multipartThreshold = 5 * 1024 * 1024; // 5MB

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

        public String getBucketContent() { return bucketContent; }
        public void setBucketContent(String bucketContent) { this.bucketContent = bucketContent; }

        public String getBucketTmp() { return bucketTmp; }
        public void setBucketTmp(String bucketTmp) { this.bucketTmp = bucketTmp; }

        public boolean isOneBucketMode() { return oneBucketMode; }
        public void setOneBucketMode(boolean oneBucketMode) { this.oneBucketMode = oneBucketMode; }

        public String getContentPrefix() { return contentPrefix; }
        public void setContentPrefix(String contentPrefix) { this.contentPrefix = contentPrefix; }

        public String getTmpPrefix() { return tmpPrefix; }
        public void setTmpPrefix(String tmpPrefix) { this.tmpPrefix = tmpPrefix; }

        public boolean isHybridMode() { return hybridMode; }
        public void setHybridMode(boolean hybridMode) { this.hybridMode = hybridMode; }

        public boolean isHandlesContent() { return handlesContent; }
        public void setHandlesContent(boolean handlesContent) { this.handlesContent = handlesContent; }

        public boolean isHandlesTmp() { return handlesTmp; }
        public void setHandlesTmp(boolean handlesTmp) { this.handlesTmp = handlesTmp; }

        public int getMaxConcurrentUploads() { return maxConcurrentUploads; }
        public void setMaxConcurrentUploads(int maxConcurrentUploads) { this.maxConcurrentUploads = maxConcurrentUploads; }

        public long getMultipartThreshold() { return multipartThreshold; }
        public void setMultipartThreshold(long multipartThreshold) { this.multipartThreshold = multipartThreshold; }
    }
}
