package com.atex.plugins.copyfit;

public class AttributeContent {
    private String name;
    private String channel;
    private String modificationTime;
    private String content;
    private boolean hasContent;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getModificationTime() { return modificationTime; }
    public void setModificationTime(String modificationTime) { this.modificationTime = modificationTime; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean getHasContent() { return hasContent; }
    public void setHasContent(boolean hasContent) { this.hasContent = hasContent; }
}
