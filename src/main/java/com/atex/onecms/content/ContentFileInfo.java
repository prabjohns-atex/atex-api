package com.atex.onecms.content;

/**
 * Metadata for files associated with content.
 */
public class ContentFileInfo {
    private String filePath;
    private String fileUri;

    public ContentFileInfo() {}

    public ContentFileInfo(String filePath, String fileUri) {
        this.filePath = filePath;
        this.fileUri = fileUri;
    }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFileUri() { return fileUri; }
    public void setFileUri(String fileUri) { this.fileUri = fileUri; }
}
