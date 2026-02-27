package com.atex.onecms.app.dam.publish;

public class PublishResult {
    private final String id;
    private final String fileUri;

    public PublishResult(String id, String fileUri) {
        this.id = id;
        this.fileUri = fileUri;
    }

    public String getId() { return id; }
    public String getFileUri() { return fileUri; }

    @Override
    public String toString() {
        return "PublishResult{id='" + id + "', fileUri='" + fileUri + "'}";
    }
}
