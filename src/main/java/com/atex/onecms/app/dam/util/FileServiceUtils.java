package com.atex.onecms.app.dam.util;

import com.atex.onecms.content.ContentFileInfo;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileInfo;
import com.atex.onecms.content.files.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Utility methods for file service operations.
 * Handles file export, writing, and URI transformation.
 */
public class FileServiceUtils {

    private static final Logger LOG = LoggerFactory.getLogger(FileServiceUtils.class);

    public static final String FILE_TRANSFER_SERVICE = "file-transfer";
    public static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    /**
     * Transform a file URI to a dot-notation path suitable for filesystem storage.
     * e.g., "content://host/path/to/file.jpg" -> "host.path.to.file.jpg"
     */
    public static String transformFileUri(String uri) {
        if (uri == null) return null;
        // Remove scheme
        int schemeEnd = uri.indexOf("://");
        if (schemeEnd >= 0) {
            uri = uri.substring(schemeEnd + 3);
        }
        return uri.replace('/', '.');
    }

    /**
     * Generate a filename from a content ID and original path.
     */
    public static String getName(String id, String path) {
        String ext = "";
        if (path != null) {
            int dotIdx = path.lastIndexOf('.');
            if (dotIdx >= 0) {
                ext = path.substring(dotIdx);
            }
        }
        if (id != null) {
            id = id.replace(':', '_');
        }
        return id + ext;
    }

    /**
     * Write text data to a file.
     */
    public static void write(String directory, String name, String data) throws IOException {
        Path dir = Path.of(cleanPath(directory));
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        Files.writeString(file, data);
    }

    /**
     * Write binary data from a stream to a file.
     */
    public static void write(String directory, String name, InputStream is) throws IOException {
        Path dir = Path.of(cleanPath(directory));
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(file));
             BufferedInputStream bis = new BufferedInputStream(is)) {
            bis.transferTo(bos);
        }
    }

    /**
     * Create a temporary folder.
     */
    public static Path createTempFolder(String parent, String prefix) throws IOException {
        Path parentPath = Path.of(cleanPath(parent));
        Files.createDirectories(parentPath);
        return Files.createTempDirectory(parentPath, prefix);
    }

    /**
     * Clean a path by normalizing separators.
     */
    public static String cleanPath(String path) {
        if (path == null) return "";
        return path.replace('\\', '/');
    }

    /**
     * Download files from the file service and write them to a directory.
     * Used for content export operations.
     */
    public static void downloadFiles(Map<String, ContentFileInfo> files, FileService fileService,
                                      String outputDir, String contentId) throws IOException {
        if (files == null || files.isEmpty()) return;

        for (Map.Entry<String, ContentFileInfo> entry : files.entrySet()) {
            String key = entry.getKey();
            ContentFileInfo fileInfo = entry.getValue();
            String fileUri = fileInfo.getFileUri();

            if (fileUri == null) continue;

            try (InputStream is = fileService.getFile(fileUri, SYSTEM_SUBJECT)) {
                if (is != null) {
                    String filename = getName(contentId, fileInfo.getFilePath());
                    write(outputDir, filename, is);
                    LOG.debug("Downloaded file {} to {}/{}", fileUri, outputDir, filename);
                } else {
                    LOG.warn("File not found in file service: {}", fileUri);
                }
            }
        }
    }

    /**
     * Get the MIME type for a file extension.
     */
    public static String getMimeType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }
}
