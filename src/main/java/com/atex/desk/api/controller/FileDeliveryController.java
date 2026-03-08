package com.atex.desk.api.controller;

import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.service.ContentService;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileInfo;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File delivery endpoint.
 * Serves files associated with content objects, supporting HTTP Range requests.
 *
 * Ported from Polopoly's filedelivery-service WAR.
 */
@RestController
@RequestMapping("/filedelivery")
@Tag(name = "File Delivery")
public class FileDeliveryController {

    private static final Logger LOG = LoggerFactory.getLogger(FileDeliveryController.class);
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes\\s*=\\s*(\\d*)\\s*-\\s*(\\d*)\\s*");
    private static final int BUFFER_SIZE = 4096;
    private static final int DEFAULT_CACHE_SECONDS = 300;

    private final FileService fileService;
    private final ContentService contentService;

    public FileDeliveryController(FileService fileService, ContentService contentService) {
        this.fileService = fileService;
        this.contentService = contentService;
    }

    @GetMapping("/contentid/{id:.+:.+}/{path:.*}")
    @Operation(summary = "Deliver file by content ID")
    public ResponseEntity<StreamingResponseBody> deliverByContentId(
            @PathVariable("id") String contentId,
            @PathVariable("path") String path,
            HttpServletRequest request) {
        return deliverFile(contentId, path, request);
    }

    @GetMapping("/{id:.+:.+}/{path:.*}")
    @Operation(summary = "Deliver file by content ID (short form)")
    public ResponseEntity<StreamingResponseBody> deliverByContentIdShort(
            @PathVariable("id") String contentId,
            @PathVariable("path") String path,
            HttpServletRequest request) {
        return deliverFile(contentId, path, request);
    }

    @GetMapping("/externalid/{externalId}/{path:.*}")
    @Operation(summary = "Deliver file by external ID")
    public ResponseEntity<StreamingResponseBody> deliverByExternalId(
            @PathVariable("externalId") String externalId,
            @PathVariable("path") String path,
            HttpServletRequest request) {
        Optional<String> contentId = contentService.resolveExternalId(externalId);
        if (contentId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return deliverFile(contentId.get(), path, request);
    }

    @GetMapping("")
    @Operation(summary = "Ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("OK");
    }

    private ResponseEntity<StreamingResponseBody> deliverFile(String contentId, String requestedPath,
                                                               HttpServletRequest request) {
        String fileUri = resolveFileUri(contentId, requestedPath);
        if (fileUri == null) {
            return ResponseEntity.notFound().build();
        }

        FileInfo fileInfo = fileService.getFileInfo(fileUri, Subject.NOBODY_CALLER);
        if (fileInfo == null) {
            return ResponseEntity.notFound().build();
        }

        String rangeHeader = request.getHeader("Range");
        String ifRange = request.getHeader("If-Range");
        String etag = "\"" + fileUri.hashCode() + "\"";

        if (ifRange != null && !ifRange.equals(etag)) {
            rangeHeader = null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("ETag", etag);
        headers.set("Accept-Ranges", "bytes");
        headers.setCacheControl(CacheControl.maxAge(DEFAULT_CACHE_SECONDS, TimeUnit.SECONDS));

        if (fileInfo.getMimeType() != null) {
            headers.setContentType(MediaType.parseMediaType(fileInfo.getMimeType()));
        }

        long fileLength = fileInfo.getLength();

        // Handle Range request
        if (rangeHeader != null) {
            long[] range = parseRange(rangeHeader, fileLength);
            if (range != null) {
                long from = range[0];
                long to = range[1];
                long rangeLength = to - from + 1;

                headers.set("Content-Range", "bytes " + from + "-" + to + "/" + fileLength);
                headers.setContentLength(rangeLength);

                StreamingResponseBody stream = output -> {
                    try (InputStream is = fileService.getFile(fileUri, from, Subject.NOBODY_CALLER)) {
                        if (is != null) {
                            streamBytes(is, output, rangeLength);
                        }
                    }
                };

                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .headers(headers)
                        .body(stream);
            }
        }

        headers.setContentLength(fileLength);

        StreamingResponseBody stream = output -> {
            try (InputStream is = fileService.getFile(fileUri, Subject.NOBODY_CALLER)) {
                if (is != null) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                }
            }
        };

        return ResponseEntity.ok()
                .headers(headers)
                .body(stream);
    }

    /**
     * Resolve a file URI from content by looking up the "files" aspect.
     */
    @SuppressWarnings("unchecked")
    private String resolveFileUri(String contentId, String requestedPath) {
        try {
            // Parse content ID: delegationId:key or delegationId:key:version
            String[] parts = contentId.split(":");
            if (parts.length < 2) return null;

            String delegationId = parts[0];
            String key = parts[1];

            // Resolve to latest version
            Optional<String> versionedId = contentService.resolve(delegationId, key);
            if (versionedId.isEmpty()) return null;

            String[] vParts = versionedId.get().split(":");
            if (vParts.length < 3) return null;

            Optional<ContentResultDto> content = contentService.getContent(vParts[0], vParts[1], vParts[2]);
            if (content.isEmpty()) return null;

            Map<String, AspectDto> aspects = content.get().getAspects();
            if (aspects == null) return null;

            // Look for "files" aspect
            AspectDto filesAspect = aspects.get("files");
            if (filesAspect == null || filesAspect.getData() == null) return null;

            Object filesObj = filesAspect.getData().get("files");
            if (filesObj instanceof Map<?, ?> filesMap) {
                // Try to match by path
                if (requestedPath != null && !requestedPath.isEmpty()) {
                    Object entry = filesMap.get(requestedPath);
                    if (entry instanceof Map<?, ?> fileEntry) {
                        Object fileUri = fileEntry.get("fileUri");
                        if (fileUri != null) return fileUri.toString();
                    }
                }

                // Fallback: return first file URI
                for (Object value : filesMap.values()) {
                    if (value instanceof Map<?, ?> fileEntry) {
                        Object fileUri = fileEntry.get("fileUri");
                        if (fileUri != null) return fileUri.toString();
                    }
                }
            }

            return null;
        } catch (Exception e) {
            LOG.error("Error resolving file URI for content {}: {}", contentId, e.getMessage());
            return null;
        }
    }

    private void streamBytes(InputStream is, java.io.OutputStream output, long length) throws java.io.IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = length;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int bytesRead = is.read(buffer, 0, toRead);
            if (bytesRead == -1) break;
            output.write(buffer, 0, bytesRead);
            remaining -= bytesRead;
        }
    }

    private long[] parseRange(String rangeHeader, long fileLength) {
        Matcher m = RANGE_PATTERN.matcher(rangeHeader);
        if (!m.matches()) return null;

        String fromStr = m.group(1);
        String toStr = m.group(2);

        long from, to;

        if (fromStr.isEmpty() && toStr.isEmpty()) {
            return null;
        } else if (fromStr.isEmpty()) {
            long suffix = Long.parseLong(toStr);
            from = fileLength - suffix;
            to = fileLength - 1;
        } else if (toStr.isEmpty()) {
            from = Long.parseLong(fromStr);
            to = fileLength - 1;
        } else {
            from = Long.parseLong(fromStr);
            to = Long.parseLong(toStr);
        }

        if (from < 0) from = 0;
        if (to >= fileLength) to = fileLength - 1;
        if (from > fileLength) return null;

        return new long[]{from, to};
    }
}
