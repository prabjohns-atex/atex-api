package com.atex.desk.api.file;

import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileInfo;
import com.atex.onecms.content.files.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

/**
 * Delegating FileService that routes requests to the appropriate backend
 * based on URI scheme and configuration.
 *
 * Ported from Polopoly's FileServiceDelegator, simplified for Spring Boot.
 * Routes by URI scheme (content://, tmp://) to configured backends.
 */
public class DelegatingFileService implements FileService {

    private static final Logger LOG = LoggerFactory.getLogger(DelegatingFileService.class);

    private final FileService localService;
    private final S3FileService s3Service;

    public DelegatingFileService(FileService localService, S3FileService s3Service) {
        this.localService = localService;
        this.s3Service = s3Service;
        LOG.info("DelegatingFileService initialized with local + S3 backends");
    }

    @Override
    public FileInfo getFileInfo(String uri, Subject subject) {
        FileService delegate = resolveByUri(uri);
        return delegate.getFileInfo(uri, subject);
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
        return resolveByUri(uri).getFile(uri, subject);
    }

    @Override
    public InputStream getFile(String uri, long offset, Subject subject) {
        return resolveByUri(uri).getFile(uri, offset, subject);
    }

    @Override
    public FileInfo uploadFile(String space, String host, String path,
                               InputStream data, String mimeType, Subject subject) {
        FileService delegate = resolveBySpace(space);
        return delegate.uploadFile(space, host, path, data, mimeType, subject);
    }

    @Override
    public FileInfo removeFile(String uri, Subject subject) {
        return resolveByUri(uri).removeFile(uri, subject);
    }

    @Override
    public FileInfo moveFile(String uri, String space, String host, Subject subject) {
        // Move may cross backends (e.g., tmp→content).
        // Use the destination backend for the move operation.
        FileService delegate = resolveBySpace(space);
        return delegate.moveFile(uri, space, host, subject);
    }

    @Override
    public FileInfo[] listFiles(String space, String host, Subject subject) {
        return resolveBySpace(space).listFiles(space, host, subject);
    }

    private FileService resolveByUri(String uri) {
        if (s3Service != null && s3Service.handles(uri)) {
            return s3Service;
        }
        return localService;
    }

    private FileService resolveBySpace(String space) {
        if (s3Service != null && s3Service.handlesSpace(space)) {
            return s3Service;
        }
        return localService;
    }
}
