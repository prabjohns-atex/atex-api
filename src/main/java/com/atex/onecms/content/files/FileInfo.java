package com.atex.onecms.content.files;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Metadata for a file stored in the file service.
 */
public class FileInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String _uri;
    private String _originalPath;
    private String _mimeType;
    private byte[] _checksum;
    private long _length;
    private long _creationTime = -1;
    private long _modifiedTime = -1;
    private long _accessTime = -1;

    public FileInfo() {}

    public FileInfo(String uri, String originalPath, String mimeType,
                    byte[] checksum, long length,
                    long creationTime, long modifiedTime, long accessTime) {
        this._uri = uri;
        this._originalPath = originalPath;
        this._mimeType = mimeType;
        this._checksum = checksum;
        this._length = length;
        this._creationTime = creationTime;
        this._modifiedTime = modifiedTime;
        this._accessTime = accessTime;
    }

    public FileInfo(FileInfo fi) {
        this._uri = fi._uri;
        this._originalPath = fi._originalPath;
        this._mimeType = fi._mimeType;
        this._checksum = fi._checksum != null ? fi._checksum.clone() : null;
        this._length = fi._length;
        this._creationTime = fi._creationTime;
        this._modifiedTime = fi._modifiedTime;
        this._accessTime = fi._accessTime;
    }

    public String getUri() { return _uri; }
    public void setUri(String uri) { this._uri = uri; }

    public String getOriginalPath() { return _originalPath; }
    public void setOriginalPath(String originalPath) { this._originalPath = originalPath; }

    public String getMimeType() { return _mimeType; }
    public void setMimeType(String mimeType) { this._mimeType = mimeType; }

    public byte[] getChecksum() { return _checksum; }
    public void setChecksum(byte[] checksum) { this._checksum = checksum; }

    public long getLength() { return _length; }
    public void setLength(long length) { this._length = length; }

    public long getCreationTime() { return _creationTime; }
    public void setCreationTime(long creationTime) { this._creationTime = creationTime; }

    public long getModifiedTime() { return _modifiedTime; }
    public void setModifiedTime(long modifiedTime) { this._modifiedTime = modifiedTime; }

    public long getAccessTime() { return _accessTime; }
    public void setAccessTime(long accessTime) { this._accessTime = accessTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileInfo fi)) return false;
        return Objects.equals(_uri, fi._uri);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_uri);
    }

    @Override
    public String toString() {
        return "FileInfo{uri='" + _uri + "', mimeType='" + _mimeType +
               "', length=" + _length + "}";
    }
}
