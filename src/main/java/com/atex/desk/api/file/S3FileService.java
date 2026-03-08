package com.atex.desk.api.file;

import com.atex.desk.api.config.FileServiceProperties;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileInfo;
import com.atex.onecms.content.files.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Amazon S3-backed FileService implementation.
 * Ported from Polopoly's FileServiceS3.
 *
 * URI scheme mapping:
 * - "content://{host}/{path}" → bucket=bucketContent, key={host}/{path}
 * - "tmp://{host}/{path}"     → bucket=bucketTmp, key={host}/{path}
 * - In one-bucket mode, uses a single bucket with prefix separation.
 */
public class S3FileService implements FileService {

    private static final Logger LOG = LoggerFactory.getLogger(S3FileService.class);
    private static final String META_SUFFIX = ".meta";
    private static final DateTimeFormatter DATE_PREFIX_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd/HH");

    private final S3Client s3Client;
    private final FileServiceProperties.S3Properties config;

    public S3FileService(FileServiceProperties.S3Properties config) {
        this.config = config;
        this.s3Client = buildS3Client(config);
        LOG.info("S3FileService initialized: region={}, content-bucket={}, tmp-bucket={}, one-bucket={}",
                config.getRegion(), config.getBucketContent(), config.getBucketTmp(),
                config.isOneBucketMode());
    }

    private S3Client buildS3Client(FileServiceProperties.S3Properties config) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getRegion()));

        if (config.getEndpoint() != null && !config.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
            builder.forcePathStyle(true);
        }

        if (config.getAccessKey() != null && !config.getAccessKey().isBlank()
                && config.getSecretKey() != null && !config.getSecretKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    @Override
    public FileInfo getFileInfo(String uri, Subject subject) {
        S3ObjectPath s3Path = resolveUri(uri);
        if (s3Path == null) return null;

        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Path.bucket)
                    .key(s3Path.key)
                    .build());

            return new FileInfo(uri, extractFilename(uri), head.contentType(),
                    null, head.contentLength(),
                    head.lastModified() != null ? head.lastModified().toEpochMilli() : -1,
                    head.lastModified() != null ? head.lastModified().toEpochMilli() : -1,
                    -1);
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            LOG.error("S3 error getting file info for {}: {}", uri, e.getMessage());
            return null;
        }
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
        S3ObjectPath s3Path = resolveUri(uri);
        if (s3Path == null) return null;

        try {
            GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                    .bucket(s3Path.bucket)
                    .key(s3Path.key);

            if (offset > 0) {
                requestBuilder.range("bytes=" + offset + "-");
            }

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(requestBuilder.build());
            return response;
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            LOG.error("S3 error getting file {}: {}", uri, e.getMessage());
            return null;
        }
    }

    @Override
    public FileInfo uploadFile(String space, String host, String path,
                               InputStream data, String mimeType, Subject subject) {
        String filename = generateFilename(path);
        String datePrefix = getDatePrefix();
        String fullPath = datePrefix + "/" + filename;
        String uri = space + "://" + host + "/" + fullPath;

        S3ObjectPath s3Path = resolveUri(uri);
        if (s3Path == null) {
            LOG.error("Cannot resolve S3 path for space={}, host={}", space, host);
            return null;
        }

        try {
            byte[] bytes = readAllBytes(data);
            byte[] checksum = computeMd5(bytes);

            PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                    .bucket(s3Path.bucket)
                    .key(s3Path.key);

            if (mimeType != null) {
                putBuilder.contentType(mimeType);
            }

            // Store original path as metadata
            putBuilder.metadata(java.util.Map.of(
                    "original-path", path != null ? path : filename
            ));

            s3Client.putObject(putBuilder.build(), RequestBody.fromBytes(bytes));

            long now = System.currentTimeMillis();
            return new FileInfo(uri, path, mimeType, checksum, bytes.length, now, now, now);
        } catch (Exception e) {
            LOG.error("Failed to upload file to S3: {}", uri, e);
            return null;
        }
    }

    @Override
    public FileInfo removeFile(String uri, Subject subject) {
        S3ObjectPath s3Path = resolveUri(uri);
        if (s3Path == null) return null;

        FileInfo info = getFileInfo(uri, subject);
        if (info == null) return null;

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Path.bucket)
                    .key(s3Path.key)
                    .build());
            return info;
        } catch (S3Exception e) {
            LOG.error("Failed to remove file from S3: {}", uri, e);
            return null;
        }
    }

    @Override
    public FileInfo moveFile(String uri, String space, String host, Subject subject) {
        S3ObjectPath sourcePath = resolveUri(uri);
        if (sourcePath == null) return null;

        String filename = extractFilename(uri);
        String newUri = space + "://" + host + "/" + filename;
        S3ObjectPath destPath = resolveUri(newUri);
        if (destPath == null) return null;

        try {
            // Copy to new location
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(sourcePath.bucket)
                    .sourceKey(sourcePath.key)
                    .destinationBucket(destPath.bucket)
                    .destinationKey(destPath.key)
                    .build();
            s3Client.copyObject(copyRequest);

            // Delete original
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(sourcePath.bucket)
                    .key(sourcePath.key)
                    .build());

            return getFileInfo(newUri, subject);
        } catch (S3Exception e) {
            LOG.error("Failed to move file from {} to {} in S3", uri, newUri, e);
            return null;
        }
    }

    @Override
    public FileInfo[] listFiles(String space, String host, Subject subject) {
        String prefix = host + "/";
        String bucket = getBucket(space);
        if (config.isOneBucketMode()) {
            prefix = getSpacePrefix(space) + "/" + prefix;
        }

        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build());

            List<FileInfo> results = new ArrayList<>();
            for (S3Object obj : response.contents()) {
                String key = obj.key();
                String fileUri = space + "://" + reconstructPathFromKey(key, space);
                results.add(new FileInfo(fileUri, extractFilenameFromKey(key),
                        null, null, obj.size(),
                        obj.lastModified() != null ? obj.lastModified().toEpochMilli() : -1,
                        obj.lastModified() != null ? obj.lastModified().toEpochMilli() : -1,
                        -1));
            }
            return results.toArray(new FileInfo[0]);
        } catch (S3Exception e) {
            LOG.error("Failed to list files in S3: {}/{}", space, host, e);
            return new FileInfo[0];
        }
    }

    /**
     * Check if this service handles the given URI scheme.
     */
    public boolean handles(String uri) {
        if (uri == null) return false;
        if (uri.startsWith("content://")) return config.isHandlesContent();
        if (uri.startsWith("tmp://")) return config.isHandlesTmp();
        if (uri.startsWith("s3://")) return true;
        return false;
    }

    /**
     * Check if this service handles the given space.
     */
    public boolean handlesSpace(String space) {
        if ("content".equals(space)) return config.isHandlesContent();
        if ("tmp".equals(space)) return config.isHandlesTmp();
        return false;
    }

    // --- Internal helpers ---

    private S3ObjectPath resolveUri(String uri) {
        if (uri == null) return null;
        int schemeEnd = uri.indexOf("://");
        if (schemeEnd < 0) return null;

        String space = uri.substring(0, schemeEnd);
        String rest = uri.substring(schemeEnd + 3);
        int slashIdx = rest.indexOf('/');
        if (slashIdx < 0) return null;

        String host = rest.substring(0, slashIdx);
        String path = rest.substring(slashIdx + 1);

        String bucket = getBucket(space);
        String key;

        if (config.isOneBucketMode()) {
            key = getSpacePrefix(space) + "/" + host + "/" + path;
        } else {
            key = host + "/" + path;
        }

        return new S3ObjectPath(bucket, key);
    }

    private String getBucket(String space) {
        if (config.isOneBucketMode()) {
            return config.getBucketContent(); // single bucket
        }
        return switch (space) {
            case "content" -> config.getBucketContent();
            case "tmp" -> config.getBucketTmp();
            default -> config.getBucketContent();
        };
    }

    private String getSpacePrefix(String space) {
        return switch (space) {
            case "content" -> config.getContentPrefix();
            case "tmp" -> config.getTmpPrefix();
            default -> space;
        };
    }

    private String reconstructPathFromKey(String key, String space) {
        if (config.isOneBucketMode()) {
            String prefix = getSpacePrefix(space) + "/";
            if (key.startsWith(prefix)) {
                key = key.substring(prefix.length());
            }
        }
        return key;
    }

    private String extractFilename(String uri) {
        int lastSlash = uri.lastIndexOf('/');
        return lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;
    }

    private String extractFilenameFromKey(String key) {
        int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
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

    private String getDatePrefix() {
        return LocalDateTime.now().format(DATE_PREFIX_FORMAT);
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }

    private byte[] computeMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(data);
        } catch (Exception e) {
            return null;
        }
    }

    private record S3ObjectPath(String bucket, String key) {}
}
