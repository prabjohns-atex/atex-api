package com.atex.desk.api.controller;

import com.atex.desk.api.config.ImageServiceProperties;
import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.service.ContentService;
import com.atex.onecms.app.dam.ws.ContentApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Image service controller — resolves content IDs to file URIs and redirects
 * to the Rust image processing sidecar (desk-image).
 *
 * Supports Polopoly-compatible URL formats:
 * - GET /image/{delegationId}:{key}/{filename}
 * - GET /image/{delegationId}:{key}:{version}/{filename}
 * - GET /image/contentid/{delegationId}:{key}:{version}/{filename}
 * - GET /image/original/{delegationId}:{key}/{filename} (download original)
 */
@RestController
@RequestMapping("/image")
@Tag(name = "Image Service")
@ConditionalOnProperty(name = "desk.image-service.enabled", havingValue = "true")
public class ImageController {

    private static final Logger LOG = LoggerFactory.getLogger(ImageController.class);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final ContentService contentService;
    private final ImageServiceProperties imageProps;

    public ImageController(ContentService contentService,
                           ImageServiceProperties imageProps) {
        this.contentService = contentService;
        this.imageProps = imageProps;
        LOG.info("ImageController enabled, sidecar URL: {}", imageProps.getUrl());
    }

    /**
     * Serve image by unversioned content ID: /image/{delegationId}:{key}/{filename}
     */
    @GetMapping("/{id:[^/]+:[^/]+}/{filename:.+}")
    @Operation(summary = "Get processed image by content ID")
    public ResponseEntity<Void> getImage(
            @PathVariable("id") String id,
            @PathVariable("filename") String filename,
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request) {

        return resolveAndRedirect(id, filename, queryParams);
    }

    /**
     * Serve image by versioned content ID: /image/contentid/{delegationId}:{key}:{version}/{filename}
     */
    @GetMapping("/contentid/{id:.+}/{filename:.+}")
    @Operation(summary = "Get processed image by versioned content ID")
    public ResponseEntity<Void> getImageByVersionedId(
            @PathVariable("id") String id,
            @PathVariable("filename") String filename,
            @RequestParam Map<String, String> queryParams) {

        return resolveAndRedirect(id, filename, queryParams);
    }

    /**
     * Download original (unprocessed) image: /image/original/{id}/{filename}
     */
    @GetMapping("/original/{id:.+}/{filename:.+}")
    @Operation(summary = "Download original image")
    public ResponseEntity<Void> getOriginal(
            @PathVariable("id") String id,
            @PathVariable("filename") String filename) {
        return redirectToOriginal(id);
    }

    /**
     * Download original without filename: /image/original/{id}
     */
    @GetMapping("/original/{id:[^/]+:[^/]+}")
    @Operation(summary = "Download original image (no filename)")
    public ResponseEntity<Void> getOriginalNoFilename(
            @PathVariable("id") String id) {
        return redirectToOriginal(id);
    }

    /**
     * File-service scheme path: /image/{scheme}/{host}/{path}
     * Proxies image processing for files addressed by URI scheme.
     * Publicly accessible (no HMAC required), matching Polopoly behavior.
     */
    @GetMapping("/{scheme:content|tmp|s3}/{host}/{path:.*}")
    @Operation(summary = "Process image by file URI")
    public ResponseEntity<Void> getImageByFileUri(
            @PathVariable("scheme") String scheme,
            @PathVariable("host") String host,
            @PathVariable("path") String path,
            @RequestParam Map<String, String> queryParams) {

        String fileUri = scheme + "/" + host + "/" + path;
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        String sidecarPath = fileUri + "/" + filename;

        Map<String, String> params = new TreeMap<>(queryParams);
        String signedQuery = buildSignedQuery(sidecarPath, params);
        String redirectUrl = imageProps.getUrl() + "/image/" + sidecarPath + "?" + signedQuery;

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .header(HttpHeaders.CACHE_CONTROL,
                        CacheControl.maxAge(imageProps.getCacheMaxAge(), TimeUnit.SECONDS)
                                .cachePublic().getHeaderValue())
                .build();
    }

    private ResponseEntity<Void> redirectToOriginal(String id) {
        ContentImageInfo info = resolveImageInfo(id);
        String redirectUrl = "/file/" + info.fileUri;

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .header(HttpHeaders.CACHE_CONTROL,
                        CacheControl.maxAge(imageProps.getCacheMaxAge(), TimeUnit.SECONDS)
                                .cachePublic().getHeaderValue())
                .build();
    }

    private ResponseEntity<Void> resolveAndRedirect(String id, String filename,
                                                     Map<String, String> queryParams) {
        ContentImageInfo info = resolveImageInfo(id);

        // Build signed redirect URL to the Rust sidecar
        // Path format: /image/{file_uri}/{filename}
        String fileUri = info.fileUri;
        String sidecarPath = fileUri + "/" + filename;

        // Add image edit info from content aspects
        Map<String, String> params = new TreeMap<>(queryParams);
        applyEditInfo(params, info);

        // Sign the URL
        String signedQuery = buildSignedQuery(sidecarPath, params);
        String redirectUrl = imageProps.getUrl() + "/image/" + sidecarPath + "?" + signedQuery;

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .header(HttpHeaders.CACHE_CONTROL,
                        CacheControl.maxAge(imageProps.getCacheMaxAge(), TimeUnit.SECONDS)
                                .cachePublic().getHeaderValue())
                .build();
    }

    /**
     * Resolve a content ID string to image info (file URI + edit metadata).
     */
    private ContentImageInfo resolveImageInfo(String idString) {
        String[] parts = contentService.parseContentId(idString);
        if (parts == null || parts.length < 2) {
            throw ContentApiException.badRequest("Invalid content ID: " + idString);
        }

        String delegationId = parts[0];
        String key = parts[1];

        Optional<ContentResultDto> result;
        if (contentService.isVersionedId(idString)) {
            result = contentService.getContent(delegationId, key, parts[2]);
        } else {
            // Resolve unversioned to latest version
            Optional<String> versionedId = contentService.resolve(delegationId, key);
            if (versionedId.isEmpty()) {
                throw ContentApiException.notFound("Content not found: " + idString);
            }
            String[] vParts = contentService.parseContentId(versionedId.get());
            result = contentService.getContent(vParts[0], vParts[1], vParts[2]);
        }

        if (result.isEmpty()) {
            throw ContentApiException.notFound("Content not found: " + idString);
        }

        ContentResultDto content = result.get();
        Map<String, AspectDto> aspects = content.getAspects();

        // Extract file URI from atex.Image aspect (ImageInfoAspectBean)
        String fileUri = null;
        if (aspects != null && aspects.containsKey("atex.Image")) {
            Map<String, Object> imageData = aspects.get("atex.Image").getData();
            if (imageData != null) {
                Object filePath = imageData.get("filePath");
                if (filePath != null) {
                    fileUri = filePath.toString();
                }
            }
        }

        if (fileUri == null) {
            throw ContentApiException.notFound("No image file for content: " + idString);
        }

        // Extract edit info from atex.ImageEditInfo aspect
        Map<String, Object> editInfo = null;
        if (aspects != null && aspects.containsKey("atex.ImageEditInfo")) {
            editInfo = aspects.get("atex.ImageEditInfo").getData();
        }

        return new ContentImageInfo(fileUri, editInfo);
    }

    /**
     * Apply ImageEditInfo (rotation, flip, focal point) to query params.
     * These are stored on the content and should be baked into the image URL.
     */
    private void applyEditInfo(Map<String, String> params, ContentImageInfo info) {
        if (info.editInfo == null) return;

        // Rotation
        Object rotation = info.editInfo.get("rotation");
        if (rotation != null) {
            int rot = ((Number) rotation).intValue();
            if (rot != 0) {
                params.putIfAbsent("rot", String.valueOf(rot));
            }
        }

        // Flip
        Object flipV = info.editInfo.get("flipVertical");
        if (Boolean.TRUE.equals(flipV)) {
            params.putIfAbsent("flipv", "1");
        }
        Object flipH = info.editInfo.get("flipHorizontal");
        if (Boolean.TRUE.equals(flipH)) {
            params.putIfAbsent("fliph", "1");
        }

        // Focal point
        Object focalPoint = info.editInfo.get("focalPoint");
        if (focalPoint instanceof Map<?, ?> fpMap) {
            Object x = fpMap.get("x");
            Object y = fpMap.get("y");
            Object zoom = fpMap.get("zoom");
            if (x != null && y != null) {
                String fpStr = x + "," + y;
                if (zoom != null) fpStr += "," + zoom;
                params.putIfAbsent("fp", fpStr);
            }
        }

        // Named format crops — if a format (f=) is requested, look up its crop
        String format = params.get("f");
        if (format != null && !format.isEmpty()) {
            Object crops = info.editInfo.get("crops");
            if (crops instanceof Map<?, ?> cropsMap) {
                Object cropInfo = cropsMap.get(format);
                if (cropInfo instanceof Map<?, ?> cropMap) {
                    Object rect = cropMap.get("cropRectangle");
                    if (rect instanceof Map<?, ?> rectMap) {
                        Object cx = rectMap.get("x");
                        Object cy = rectMap.get("y");
                        Object cw = rectMap.get("width");
                        Object ch = rectMap.get("height");
                        if (cx != null && cy != null && cw != null && ch != null) {
                            params.put("c", cx + "," + cy + "," + cw + "," + ch);
                        }
                    }
                }
            }
        }
    }

    /**
     * Build HMAC-signed query string.
     * Format matches Polopoly: signature param key = "$p$w$h$..." (sorted),
     * value = first N hex chars of HMAC-SHA256.
     */
    private String buildSignedQuery(String path, Map<String, String> params) {
        // Sort param keys
        TreeMap<String, String> sorted = new TreeMap<>(params);

        // Build signature key and hash input
        StringBuilder sigKey = new StringBuilder("$p");
        StringBuilder hashInput = new StringBuilder(path);

        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("$")) continue; // Skip existing signatures
            sigKey.append("$").append(key);
            hashInput.append(entry.getValue());
        }

        // Compute HMAC
        String signature = computeHmac(hashInput.toString());

        // Build query string
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (entry.getKey().startsWith("$")) continue;
            if (!query.isEmpty()) query.append("&");
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        // Append signature
        if (!query.isEmpty()) query.append("&");
        query.append(URLEncoder.encode(sigKey.toString(), StandardCharsets.UTF_8))
                .append("=")
                .append(signature);

        return query.toString();
    }

    private String computeHmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(
                    imageProps.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            String hex = bytesToHex(hash);
            return hex.substring(0, Math.min(imageProps.getSignatureLength(), hex.length()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC signature", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Holds resolved image content info.
     */
    private record ContentImageInfo(String fileUri, Map<String, Object> editInfo) {}
}
