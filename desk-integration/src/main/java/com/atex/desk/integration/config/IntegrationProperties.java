package com.atex.desk.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for desk-integration.
 * Mapped from {@code desk.integration.*} in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "desk.integration")
public class IntegrationProperties {

    /** Base directory for incoming wire feed files. */
    private Path feedBaseDir = Path.of("/data/feeds");

    /** Base directory for error/failed files. */
    private Path errorDir = Path.of("/data/feeds/error");

    /** Base directory for backup/processed files. */
    private Path backupDir = Path.of("/data/feeds/backup");

    /** Polling interval for feed directories in milliseconds. */
    private long pollIntervalMs = 10_000;

    /** File stability check delay in milliseconds (wait for file to finish writing). */
    private long fileStabilityDelayMs = 2_000;

    /** Maximum number of files to process per poll cycle. */
    private int maxFilesPerPoll = 100;

    /** Default security parent for imported content. */
    private String defaultSecurityParent = "dam.assets.production.d";

    /** Default insert parent for imported content. */
    private String defaultInsertParent = "p.siteengine.Sites.d";

    /** Feed source configurations keyed by feed name. */
    private Map<String, FeedSourceConfig> feeds = new HashMap<>();

    /** Purge/housekeeping configuration. */
    private PurgeConfig purge = new PurgeConfig();

    /** Distribution/send configuration. */
    private DistributionConfig distribution = new DistributionConfig();

    // Getters/setters

    public Path getFeedBaseDir() { return feedBaseDir; }
    public void setFeedBaseDir(Path feedBaseDir) { this.feedBaseDir = feedBaseDir; }

    public Path getErrorDir() { return errorDir; }
    public void setErrorDir(Path errorDir) { this.errorDir = errorDir; }

    public Path getBackupDir() { return backupDir; }
    public void setBackupDir(Path backupDir) { this.backupDir = backupDir; }

    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }

    public long getFileStabilityDelayMs() { return fileStabilityDelayMs; }
    public void setFileStabilityDelayMs(long v) { this.fileStabilityDelayMs = v; }

    public int getMaxFilesPerPoll() { return maxFilesPerPoll; }
    public void setMaxFilesPerPoll(int maxFilesPerPoll) { this.maxFilesPerPoll = maxFilesPerPoll; }

    public String getDefaultSecurityParent() { return defaultSecurityParent; }
    public void setDefaultSecurityParent(String v) { this.defaultSecurityParent = v; }

    public String getDefaultInsertParent() { return defaultInsertParent; }
    public void setDefaultInsertParent(String v) { this.defaultInsertParent = v; }

    public Map<String, FeedSourceConfig> getFeeds() { return feeds; }
    public void setFeeds(Map<String, FeedSourceConfig> feeds) { this.feeds = feeds; }

    public PurgeConfig getPurge() { return purge; }
    public void setPurge(PurgeConfig purge) { this.purge = purge; }

    public DistributionConfig getDistribution() { return distribution; }
    public void setDistribution(DistributionConfig d) { this.distribution = d; }

    /**
     * Configuration for a single feed source (e.g., AFP images, Reuters text).
     */
    public static class FeedSourceConfig {
        private String directory;
        private String type = "article"; // article, image
        private String parserClass;
        private String encoding = "UTF-8";
        private String securityParent;
        private String insertParent;
        private String partition = "incoming";
        private String templateName;
        private String fieldMappingFile;
        private boolean enabled = true;
        private int threadPoolSize = 4;

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getParserClass() { return parserClass; }
        public void setParserClass(String parserClass) { this.parserClass = parserClass; }
        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
        public String getSecurityParent() { return securityParent; }
        public void setSecurityParent(String v) { this.securityParent = v; }
        public String getInsertParent() { return insertParent; }
        public void setInsertParent(String v) { this.insertParent = v; }
        public String getPartition() { return partition; }
        public void setPartition(String partition) { this.partition = partition; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getFieldMappingFile() { return fieldMappingFile; }
        public void setFieldMappingFile(String v) { this.fieldMappingFile = v; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getThreadPoolSize() { return threadPoolSize; }
        public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }
    }

    /**
     * Purge/housekeeping configuration.
     */
    public static class PurgeConfig {
        private boolean enabled = false;
        private String schedule = "0 0 3 * * *"; // 3 AM daily
        private int maxDelete = 15000;
        private int solrBatchSize = 1000;
        private String trashParent = "dam.assets.trash.d";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getSchedule() { return schedule; }
        public void setSchedule(String schedule) { this.schedule = schedule; }
        public int getMaxDelete() { return maxDelete; }
        public void setMaxDelete(int maxDelete) { this.maxDelete = maxDelete; }
        public int getSolrBatchSize() { return solrBatchSize; }
        public void setSolrBatchSize(int solrBatchSize) { this.solrBatchSize = solrBatchSize; }
        public String getTrashParent() { return trashParent; }
        public void setTrashParent(String trashParent) { this.trashParent = trashParent; }
    }

    /**
     * Content distribution configuration.
     */
    public static class DistributionConfig {
        private boolean enabled = false;
        private String localBaseDir = "/data/distribution";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getLocalBaseDir() { return localBaseDir; }
        public void setLocalBaseDir(String v) { this.localBaseDir = v; }
    }
}
