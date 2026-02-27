package com.atex.desk.api.onecms;

import com.atex.onecms.content.files.FileService;
import com.polopoly.cm.client.HttpFileServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local implementation of HttpFileServiceClient.
 * Provides direct access to the local FileService.
 */
@Component
public class LocalHttpFileServiceClient implements HttpFileServiceClient {

    private final FileService fileService;
    private final String baseUrl;

    public LocalHttpFileServiceClient(FileService fileService,
                                       @Value("${desk.api-url:http://localhost:8081}") String baseUrl) {
        this.fileService = fileService;
        this.baseUrl = baseUrl;
    }

    @Override
    public String getFileServiceBaseUrl() {
        return baseUrl + "/file";
    }

    @Override
    public FileService getFileService() {
        return fileService;
    }
}
