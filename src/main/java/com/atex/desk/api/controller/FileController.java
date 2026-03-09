package com.atex.desk.api.controller;

import com.atex.onecms.app.dam.ws.ContentApiException;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileInfo;
import com.atex.onecms.content.files.FileInfoDTO;
import com.atex.onecms.content.files.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/file")
@Tag(name = "Files")
public class FileController {

    private static final Logger LOG = LoggerFactory.getLogger(FileController.class);
    private static final String X_ORIGINAL_PATH = "X-Original-Path";

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/info/{space}/{host}/{path:.*}")
    @Operation(summary = "Get file metadata", description = "Returns file info as JSON")
    public ResponseEntity<FileInfoDTO> getInfo(@PathVariable("space") String space,
                                                @PathVariable("host") String host,
                                                @PathVariable("path") String path) {
        String uri = buildUri(space, host, path);
        FileInfo fileInfo = fileService.getFileInfo(uri, Subject.NOBODY_CALLER);
        if (fileInfo == null) {
            throw ContentApiException.notFound("No such file: " + space + "/" + host + "/" + path);
        }

        HttpHeaders headers = new HttpHeaders();
        addFileInfoHeaders(fileInfo, headers);
        addCacheHeaders(headers);

        return ResponseEntity.ok()
                .headers(headers)
                .body(new FileInfoDTO(fileInfo));
    }

    @GetMapping("/metadata")
    @Operation(summary = "Get file metadata by URI", description = "Returns file info for a given URI query param")
    public ResponseEntity<FileInfoDTO> getMetadata(@RequestParam("uri") String uri) {
        FileInfo fileInfo = fileService.getFileInfo(uri, Subject.NOBODY_CALLER);
        if (fileInfo == null) {
            throw ContentApiException.notFound("No such file: " + uri);
        }
        return ResponseEntity.ok(new FileInfoDTO(fileInfo));
    }

    @PostMapping("/{space}")
    @Operation(summary = "Upload file (anonymous)", description = "Upload a file to a space; host defaults to caller login name")
    public ResponseEntity<FileInfoDTO> uploadFileAnonymous(
            @PathVariable("space") String space,
            HttpServletRequest request) {

        String host = getUserId(request);
        return doUpload(space, host, "unnamed_file", request);
    }

    @PostMapping("/{space}/{host}/{path:.*}")
    @Operation(summary = "Upload file", description = "Upload a file with explicit space/host/path")
    public ResponseEntity<FileInfoDTO> uploadFile(@PathVariable("space") String space,
                                                   @PathVariable("host") String host,
                                                   @PathVariable("path") String path,
                                                   HttpServletRequest request) {
        return doUpload(space, host, path, request);
    }

    private ResponseEntity<FileInfoDTO> doUpload(String space, String host, String path,
                                                  HttpServletRequest request) {
        String mimeType = request.getContentType();
        try (InputStream inputStream = request.getInputStream()) {
            FileInfo fileInfo = fileService.uploadFile(space, host, path, inputStream,
                    mimeType, Subject.NOBODY_CALLER);
            if (fileInfo == null) {
                throw ContentApiException.internal("Failed to upload file");
            }

            URI fileUri = URI.create(fileInfo.getUri());
            String locationPath = String.format("/file/%s/%s%s",
                    fileUri.getScheme(), fileUri.getHost(), fileUri.getPath());
            String baseUrl = request.getScheme() + "://" + request.getServerName()
                    + ":" + request.getServerPort();

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(baseUrl + locationPath));
            if (path != null) {
                headers.set(X_ORIGINAL_PATH, path);
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .headers(headers)
                    .body(new FileInfoDTO(fileInfo));
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to upload file to {}/{}/{}", space, host, path, e);
            throw ContentApiException.internal("Error uploading file: " + e.getMessage(), e);
        }
    }

    @GetMapping("/{space}/{host}/{path:.*}")
    @Operation(summary = "Download file", description = "Download binary content from the file service")
    public ResponseEntity<StreamingResponseBody> getFile(@PathVariable("space") String space,
                                                          @PathVariable("host") String host,
                                                          @PathVariable("path") String path) {
        // Fast path: resolve directly to filesystem for LocalFileService (single pass)
        java.nio.file.Path filePath = null;
        if (fileService instanceof com.atex.desk.api.onecms.LocalFileService localFs) {
            filePath = localFs.resolveToPath(space, host, path);
        }

        if (filePath != null && Files.exists(filePath)) {
            HttpHeaders headers = new HttpHeaders();
            addCacheHeaders(headers);
            try {
                headers.setContentLength(Files.size(filePath));
                long modified = Files.getLastModifiedTime(filePath).toMillis();
                if (modified > 0) headers.setLastModified(modified);
                String mimeType = Files.probeContentType(filePath);
                if (mimeType != null) {
                    headers.setContentType(MediaType.parseMediaType(mimeType));
                }
            } catch (java.io.IOException e) {
                throw ContentApiException.internal("Error reading file metadata", e);
            }

            final java.nio.file.Path fp = filePath;
            StreamingResponseBody stream = output -> {
                try (InputStream is = Files.newInputStream(fp)) {
                    is.transferTo(output);
                }
            };
            return ResponseEntity.ok().headers(headers).body(stream);
        }

        // Fallback: standard two-call approach via FileService interface (S3, etc.)
        String uri = buildUri(space, host, path);
        FileInfo fileInfo = fileService.getFileInfo(uri, Subject.NOBODY_CALLER);
        if (fileInfo == null) {
            throw ContentApiException.notFound("No such file: " + space + "/" + host + "/" + path);
        }

        InputStream inputStream = fileService.getFile(uri, Subject.NOBODY_CALLER);
        if (inputStream == null) {
            throw ContentApiException.notFound("No such file: " + space + "/" + host + "/" + path);
        }

        HttpHeaders headers = new HttpHeaders();
        addFileInfoHeaders(fileInfo, headers);
        addCacheHeaders(headers);
        headers.setContentLength(fileInfo.getLength());
        if (fileInfo.getMimeType() != null) {
            headers.setContentType(MediaType.parseMediaType(fileInfo.getMimeType()));
        }

        StreamingResponseBody stream = output -> {
            try (inputStream) {
                inputStream.transferTo(output);
            }
        };
        return ResponseEntity.ok().headers(headers).body(stream);
    }

    @DeleteMapping("/{space}/{host}/{path:.*}")
    @Operation(summary = "Delete file", description = "Delete a file from the file service")
    public ResponseEntity<Void> deleteFile(@PathVariable("space") String space,
                                            @PathVariable("host") String host,
                                            @PathVariable("path") String path) {
        String uri = buildUri(space, host, path);
        fileService.removeFile(uri, Subject.NOBODY_CALLER);
        return ResponseEntity.ok().build();
    }

    private String buildUri(String space, String host, String path) {
        return space + "://" + host + "/" + path;
    }

    private void addFileInfoHeaders(FileInfo fileInfo, HttpHeaders headers) {
        if (fileInfo.getOriginalPath() != null) {
            headers.set(X_ORIGINAL_PATH, fileInfo.getOriginalPath());
        }
        if (fileInfo.getModifiedTime() > 0) {
            headers.setLastModified(fileInfo.getModifiedTime());
        }
    }

    private void addCacheHeaders(HttpHeaders headers) {
        headers.setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate());
    }

    private String getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("desk.auth.user");
        if (userId != null) {
            return userId.toString();
        }
        return "anonymous";
    }
}
