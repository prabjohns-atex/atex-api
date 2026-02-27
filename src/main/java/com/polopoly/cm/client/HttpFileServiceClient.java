package com.polopoly.cm.client;

import com.atex.onecms.content.files.FileService;

/**
 * Client interface for file service access.
 * In the original architecture this provided HTTP access to the file service.
 * In desk-api it provides direct access to the local FileService.
 */
public interface HttpFileServiceClient {

    /**
     * Get the base URL of the file service.
     */
    String getFileServiceBaseUrl();

    /**
     * Get the underlying file service.
     */
    FileService getFileService();
}
