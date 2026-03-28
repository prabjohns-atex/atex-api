package com.atex.desk.api.file;

import java.util.Set;

/**
 * Validates file upload parameters to prevent CWE-434 (unrestricted upload)
 * and path traversal attacks. Mirrors mytype-new/lib/fileValidation.ts.
 */
public final class FileUploadValidator {

    private FileUploadValidator() {}

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "avif", "tiff", "bmp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "mp4", "mov", "avi", "mp3", "wav", "ogg",
            "zip", "svg", "json", "xml", "txt", "css", "js", "html",
            "indd", "ai", "eps", "psd"   // print/design files common in DAM
    );

    private static final Set<String> ALLOWED_MIME_PREFIXES = Set.of(
            "image/", "video/", "audio/",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument",
            "application/vnd.ms-",
            "application/zip", "application/x-zip",
            "application/json", "application/xml",
            "text/plain", "text/html", "text/css", "text/xml",
            "application/javascript",
            "application/octet-stream",      // generic binary — common for InDesign etc.
            "application/postscript"          // EPS files
    );

    /**
     * Validate a path segment (space, host, or filename) for path traversal.
     * Returns true if safe.
     */
    public static boolean isSafePathSegment(String segment) {
        if (segment == null || segment.isBlank()) return false;
        if (segment.contains("..")) return false;
        // Allow forward slashes in paths (they're valid for nested file paths)
        // but block backslashes and null bytes
        if (segment.contains("\\") || segment.contains("\0")) return false;
        return true;
    }

    /**
     * Validate host parameter — must be alphanumeric with limited special chars.
     */
    public static boolean isSafeHost(String host) {
        if (host == null || host.isBlank()) return false;
        return host.matches("^[a-zA-Z0-9_.\\-@]{1,128}$");
    }

    /**
     * Check if the file extension is in the allow list.
     */
    public static boolean isAllowedExtension(String filename) {
        if (filename == null) return false;
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == filename.length() - 1) return false;
        String ext = filename.substring(dotIdx + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext);
    }

    /**
     * Check if the declared MIME type is in the allow list.
     */
    public static boolean isAllowedMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return true; // missing is OK (will be detected)
        String lower = mimeType.toLowerCase().split(";")[0].trim(); // strip charset etc.
        for (String prefix : ALLOWED_MIME_PREFIXES) {
            if (lower.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Full validation — returns null if valid, or an error message.
     */
    public static String validate(String space, String host, String path, String mimeType) {
        if (!isSafePathSegment(space)) {
            return "Invalid space parameter";
        }
        if (!isSafeHost(host)) {
            return "Invalid host parameter";
        }
        if (!isSafePathSegment(path)) {
            return "Invalid path parameter — contains traversal characters";
        }
        if (!isAllowedExtension(path)) {
            return "File type not allowed";
        }
        if (!isAllowedMimeType(mimeType)) {
            return "MIME type not allowed: " + mimeType;
        }
        return null;
    }
}
