package com.atex.desk.integration.feed;

import com.atex.desk.integration.config.IntegrationProperties;
import com.atex.desk.integration.config.IntegrationProperties.FeedSourceConfig;
import com.atex.desk.integration.feed.parser.WireArticleParser;
import com.atex.desk.integration.feed.parser.WireImageParser;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Polls configured feed directories for new files and processes them.
 * Replaces Camel file-based routes from the legacy server-integration.
 *
 * <p>Each configured feed source (in {@code desk.integration.feeds.*}) specifies:
 * <ul>
 *   <li>directory to poll</li>
 *   <li>parser class (article or image)</li>
 *   <li>target partition and security parent</li>
 * </ul>
 *
 * <p>Files are processed, then moved to backup (success) or error (failure) directories.
 * File stability is checked before processing to avoid reading partially-written files.
 */
@Component
public class FeedPoller {

    private static final Logger LOG = Logger.getLogger(FeedPoller.class.getName());

    private final IntegrationProperties properties;
    private final FeedContentCreator contentCreator;

    /** Tracks file sizes for stability detection. */
    private final Map<String, Long> fileSizeTracker = new ConcurrentHashMap<>();

    /** Cached parser instances keyed by class name. */
    private final Map<String, Object> parserCache = new ConcurrentHashMap<>();

    public FeedPoller(IntegrationProperties properties, FeedContentCreator contentCreator) {
        this.properties = properties;
        this.contentCreator = contentCreator;
    }

    @PostConstruct
    public void init() {
        Map<String, FeedSourceConfig> feeds = properties.getFeeds();
        if (feeds.isEmpty()) {
            LOG.info("No feed sources configured");
            return;
        }
        LOG.info("Feed poller initialized with " + feeds.size() + " source(s): "
            + String.join(", ", feeds.keySet()));

        // Ensure backup/error directories exist
        try {
            Files.createDirectories(properties.getBackupDir());
            Files.createDirectories(properties.getErrorDir());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not create backup/error directories", e);
        }
    }

    /**
     * Polls all configured feed directories on a fixed interval.
     */
    @Scheduled(fixedDelayString = "${desk.integration.poll-interval-ms:10000}",
               initialDelayString = "${desk.integration.poll-initial-delay-ms:5000}")
    public void poll() {
        for (Map.Entry<String, FeedSourceConfig> entry : properties.getFeeds().entrySet()) {
            String feedName = entry.getKey();
            FeedSourceConfig config = entry.getValue();
            if (!config.isEnabled()) continue;

            try {
                pollFeed(feedName, config);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error polling feed: " + feedName, e);
            }
        }
    }

    private void pollFeed(String feedName, FeedSourceConfig config) {
        File dir = new File(config.getDirectory());
        if (!dir.isDirectory()) {
            LOG.fine(() -> "Feed directory does not exist: " + dir);
            return;
        }

        File[] files = dir.listFiles(f -> f.isFile() && !f.isHidden()
            && !f.getName().endsWith(".properties") && !f.getName().startsWith("."));
        if (files == null || files.length == 0) return;

        // Sort by modification time (oldest first)
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        int processed = 0;
        for (File file : files) {
            if (processed >= properties.getMaxFilesPerPoll()) break;

            // File stability check: skip if file size is still changing
            if (!isFileStable(file)) {
                LOG.fine(() -> "File not stable yet: " + file.getName());
                continue;
            }

            try {
                processFile(feedName, config, file);
                moveToBackup(file, feedName);
                processed++;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to process " + file.getName()
                    + " from feed " + feedName, e);
                moveToError(file, feedName);
            } finally {
                fileSizeTracker.remove(file.getAbsolutePath());
            }
        }

        if (processed > 0) {
            LOG.info("Feed " + feedName + ": processed " + processed + " file(s)");
        }
    }

    private void processFile(String feedName, FeedSourceConfig config, File file)
            throws Exception {
        String type = config.getType();

        if ("image".equals(type)) {
            WireImageParser parser = (WireImageParser) getParser(config.getParserClass());
            WireImage image = parser.parseImage(file, config.getEncoding());

            // Resolve image file (may be the file itself or referenced by parser)
            File imageFile = image.getImageFilename() != null
                ? file.toPath().getParent().resolve(image.getImageFilename()).toFile()
                : file;

            contentCreator.createImage(image, imageFile, config.getSecurityParent());

            // Move the image file too if it's separate from the metadata file
            if (image.getImageFilename() != null && !imageFile.equals(file) && imageFile.exists()) {
                moveToBackup(imageFile, feedName);
            }

        } else {
            // Default: article
            WireArticleParser parser = (WireArticleParser) getParser(config.getParserClass());
            List<WireArticle> articles = parser.parseArticles(file, config.getEncoding());

            for (WireArticle article : articles) {
                contentCreator.createArticle(article, config.getSecurityParent());
            }
        }
    }

    /**
     * Check file stability: file must have the same size on two consecutive polls.
     */
    private boolean isFileStable(File file) {
        String key = file.getAbsolutePath();
        long currentSize = file.length();
        Long previousSize = fileSizeTracker.put(key, currentSize);
        return previousSize != null && previousSize == currentSize;
    }

    private Object getParser(String className) throws Exception {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("No parser class configured");
        }
        return parserCache.computeIfAbsent(className, name -> {
            try {
                return Class.forName(name).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate parser: " + name, e);
            }
        });
    }

    private void moveToBackup(File file, String feedName) {
        moveFile(file, properties.getBackupDir().resolve(feedName));
    }

    private void moveToError(File file, String feedName) {
        moveFile(file, properties.getErrorDir().resolve(feedName));
    }

    private void moveFile(File file, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(file.getName());
            Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not move file " + file.getName()
                + " to " + targetDir, e);
        }
    }
}
