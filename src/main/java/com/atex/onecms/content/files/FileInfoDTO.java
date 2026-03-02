package com.atex.onecms.content.files;

/**
 * DTO for file metadata. Uses uppercase "URI" field name to match
 * the original Polopoly API that clients (mytype-new) expect.
 */
public class FileInfoDTO {

    private String URI;
    private long length;
    private long creationTime;
    private long modifiedTime;
    private long accessTime;
    private String mimeType;
    private String originalPath;

    public FileInfoDTO() {}

    public FileInfoDTO(FileInfo fi) {
        this.URI = fi.getUri();
        this.length = fi.getLength();
        this.creationTime = fi.getCreationTime();
        this.modifiedTime = fi.getModifiedTime();
        this.accessTime = fi.getAccessTime();
        this.mimeType = fi.getMimeType();
        this.originalPath = fi.getOriginalPath();
    }

    public String getURI() { return URI; }
    public void setURI(String URI) { this.URI = URI; }

    public long getLength() { return length; }
    public void setLength(long length) { this.length = length; }

    public long getCreationTime() { return creationTime; }
    public void setCreationTime(long creationTime) { this.creationTime = creationTime; }

    public long getModifiedTime() { return modifiedTime; }
    public void setModifiedTime(long modifiedTime) { this.modifiedTime = modifiedTime; }

    public long getAccessTime() { return accessTime; }
    public void setAccessTime(long accessTime) { this.accessTime = accessTime; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getOriginalPath() { return originalPath; }
    public void setOriginalPath(String originalPath) { this.originalPath = originalPath; }
}
