package com.atex.desk.api.onecms;

import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileInfo;
import com.atex.onecms.content.files.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Filesystem-backed FileService implementation.
 * Stores files under a configurable base directory, organized by space/host.
 *
 * URI format: {space}://{host}/{filename}
 * Storage path: {baseDir}/{space}/{host}/{filename}
 */
@Service
public class LocalFileService implements FileService {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFileService.class);

    private final Path baseDir;

    public LocalFileService(@Value("${desk.file-service.base-dir:./files}") String baseDirPath) {
        this.baseDir = Path.of(baseDirPath);
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            LOG.warn("Could not create file service base directory: {}", baseDirPath, e);
        }
    }

    @Override
    public FileInfo getFileInfo(String uri, Subject subject) {
        Path filePath = resolveUri(uri);
        if (filePath == null || !Files.exists(filePath)) {
            return null;
        }
        return buildFileInfo(uri, filePath);
    }

    @Override
    public FileInfo[] getFileInfos(String[] uris, Subject subject) {
        FileInfo[] results = new FileInfo[uris.length];
        for (int i = 0; i < uris.length; i++) {
            results[i] = getFileInfo(uris[i], subject);
        }
        return results;
    }

    @Override
    public InputStream getFile(String uri, Subject subject) {
        return getFile(uri, 0, subject);
    }

    @Override
    public InputStream getFile(String uri, long offset, Subject subject) {
        Path filePath = resolveUri(uri);
        if (filePath == null || !Files.exists(filePath)) {
            return null;
        }
        try {
            InputStream is = new BufferedInputStream(Files.newInputStream(filePath));
            if (offset > 0) {
                long skipped = is.skip(offset);
                if (skipped < offset) {
                    LOG.warn("Could only skip {} of {} bytes for URI: {}", skipped, offset, uri);
                }
            }
            return is;
        } catch (IOException e) {
            LOG.error("Failed to read file: {}", uri, e);
            return null;
        }
    }

    @Override
    public FileInfo uploadFile(String space, String host, String path,
                               InputStream data, String mimeType, Subject subject) {
        String filename = generateFilename(path);
        String uri = space + "://" + host + "/" + filename;

        Path dir = baseDir.resolve(space).resolve(host);
        Path filePath = dir.resolve(filename);

        try {
            Files.createDirectories(dir);
            long length = Files.copy(data, filePath, StandardCopyOption.REPLACE_EXISTING);

            byte[] checksum = computeMd5(filePath);
            long now = System.currentTimeMillis();

            return new FileInfo(uri, path, mimeType, checksum, length, now, now, now);
        } catch (IOException e) {
            LOG.error("Failed to upload file: {}", uri, e);
            return null;
        }
    }

    @Override
    public FileInfo removeFile(String uri, Subject subject) {
        Path filePath = resolveUri(uri);
        if (filePath == null || !Files.exists(filePath)) {
            return null;
        }
        FileInfo info = buildFileInfo(uri, filePath);
        try {
            Files.delete(filePath);
        } catch (IOException e) {
            LOG.error("Failed to remove file: {}", uri, e);
            return null;
        }
        return info;
    }

    @Override
    public FileInfo moveFile(String uri, String space, String host, Subject subject) {
        Path sourcePath = resolveUri(uri);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            return null;
        }

        String filename = sourcePath.getFileName().toString();
        String newUri = space + "://" + host + "/" + filename;
        Path destDir = baseDir.resolve(space).resolve(host);
        Path destPath = destDir.resolve(filename);

        try {
            Files.createDirectories(destDir);
            Files.move(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
            return buildFileInfo(newUri, destPath);
        } catch (IOException e) {
            LOG.error("Failed to move file from {} to {}", uri, newUri, e);
            return null;
        }
    }

    @Override
    public FileInfo[] listFiles(String space, String host, Subject subject) {
        Path dir = baseDir.resolve(space).resolve(host);
        if (!Files.isDirectory(dir)) {
            return new FileInfo[0];
        }
        List<FileInfo> results = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                String uri = space + "://" + host + "/" + p.getFileName().toString();
                results.add(buildFileInfo(uri, p));
            });
        } catch (IOException e) {
            LOG.error("Failed to list files in {}/{}", space, host, e);
        }
        return results.toArray(new FileInfo[0]);
    }

    /**
     * Resolve a URI to a filesystem path.
     * URI format: {space}://{host}/{filename}
     */
    private Path resolveUri(String uri) {
        if (uri == null) return null;
        // Parse scheme://host/path
        int schemeEnd = uri.indexOf("://");
        if (schemeEnd < 0) return null;
        String space = uri.substring(0, schemeEnd);
        String rest = uri.substring(schemeEnd + 3);
        int slashIdx = rest.indexOf('/');
        if (slashIdx < 0) return null;
        String host = rest.substring(0, slashIdx);
        String filename = rest.substring(slashIdx + 1);

        // Prevent path traversal
        Path resolved = baseDir.resolve(space).resolve(host).resolve(filename).normalize();
        if (!resolved.startsWith(baseDir)) {
            LOG.warn("Path traversal attempt detected: {}", uri);
            return null;
        }
        return resolved;
    }

    private String generateFilename(String originalPath) {
        String extension = "";
        if (originalPath != null) {
            int dotIdx = originalPath.lastIndexOf('.');
            if (dotIdx >= 0) {
                extension = originalPath.substring(dotIdx);
            }
        }
        return UUID.randomUUID().toString() + extension;
    }

    private FileInfo buildFileInfo(String uri, Path filePath) {
        try {
            long length = Files.size(filePath);
            long modified = Files.getLastModifiedTime(filePath).toMillis();
            byte[] checksum = computeMd5(filePath);
            String mimeType = Files.probeContentType(filePath);
            return new FileInfo(uri, filePath.getFileName().toString(),
                              mimeType, checksum, length, modified, modified, modified);
        } catch (IOException e) {
            LOG.error("Failed to build FileInfo for: {}", uri, e);
            return new FileInfo(uri, null, null, null, 0, -1, -1, -1);
        }
    }

    private byte[] computeMd5(Path filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] data = Files.readAllBytes(filePath);
            return md.digest(data);
        } catch (NoSuchAlgorithmException | IOException e) {
            return null;
        }
    }
}
