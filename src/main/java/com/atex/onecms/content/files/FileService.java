package com.atex.onecms.content.files;

import com.atex.onecms.content.Subject;

import java.io.InputStream;

/**
 * File storage service interface.
 * Files are organized by space (storage scheme) and host (logical grouping).
 * URIs uniquely identify files in the service.
 *
 * Supported URI schemes: content:// and tmp://
 */
public interface FileService {

    String SCHEME_CONTENT = "content";
    String SCHEME_TMP = "tmp";

    /**
     * Get metadata for a file.
     */
    FileInfo getFileInfo(String uri, Subject subject);

    /**
     * Get metadata for multiple files.
     */
    FileInfo[] getFileInfos(String[] uris, Subject subject);

    /**
     * Retrieve file data.
     */
    InputStream getFile(String uri, Subject subject);

    /**
     * Retrieve file data from an offset.
     */
    InputStream getFile(String uri, long offset, Subject subject);

    /**
     * Upload a file.
     * @param space storage scheme (content or tmp)
     * @param host logical grouping
     * @param path original file path hint
     * @param data file content
     * @param mimeType MIME type
     * @param subject caller context
     * @return metadata for the uploaded file
     */
    FileInfo uploadFile(String space, String host, String path, InputStream data, String mimeType, Subject subject);

    /**
     * Soft-delete a file.
     */
    FileInfo removeFile(String uri, Subject subject);

    /**
     * Move a file between spaces.
     */
    FileInfo moveFile(String uri, String space, String host, Subject subject);

    /**
     * List files in a space/host.
     */
    FileInfo[] listFiles(String space, String host, Subject subject);
}
