package com.atex.plugins.copyfit;

public class PrintAttribute {
    private int id;
    private String name;
    private String element;
    private String attribute;
    private String content;
    private boolean hasContent;
    private boolean hasFocus;
    private int cursorPos;
    private String lockedBy;
    private String lockedAt;
    private String modificationTime;
    private String channel;
    private int channelFlags;
    private int operationFlags;
    private int resultFlags;
    private int inProgressFlags;
    private boolean lockedLocally;
    private PrintWorkflowStatus status;
    private String edition;
    private String auxData = "{}";

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getElement() { return element; }
    public void setElement(String element) { this.element = element; }

    public String getAttribute() { return attribute; }
    public void setAttribute(String attribute) { this.attribute = attribute; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean getHasContent() { return hasContent; }
    public void setHasContent(boolean hasContent) { this.hasContent = hasContent; }
    public boolean isHasContent() { return hasContent; }

    public boolean getHasFocus() { return hasFocus; }
    public void setHasFocus(boolean hasFocus) { this.hasFocus = hasFocus; }
    public boolean isHasFocus() { return hasFocus; }

    public int getCursorPos() { return cursorPos; }
    public void setCursorPos(int cursorPos) { this.cursorPos = cursorPos; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

    public String getLockedAt() { return lockedAt; }
    public void setLockedAt(String lockedAt) { this.lockedAt = lockedAt; }

    public String getModificationTime() { return modificationTime; }
    public void setModificationTime(String modificationTime) { this.modificationTime = modificationTime; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public int getChannelFlags() { return channelFlags; }
    public void setChannelFlags(int channelFlags) { this.channelFlags = channelFlags; }

    public int getOperationFlags() { return operationFlags; }
    public void setOperationFlags(int flags) { this.operationFlags = flags; }

    public int getResultFlags() { return resultFlags; }
    public void setResultFlags(int flags) { this.resultFlags = flags; }

    public int getInProgressFlags() { return inProgressFlags; }
    public void setInProgressFlags(int flags) { this.inProgressFlags = flags; }

    public boolean getLockedLocally() { return lockedLocally; }
    public void setLockedLocally(boolean lockedLocally) { this.lockedLocally = lockedLocally; }
    public boolean isLockedLocally() { return lockedLocally; }

    public PrintWorkflowStatus getStatus() { return status; }
    public void setStatus(PrintWorkflowStatus status) { this.status = status; }

    public String getEdition() { return edition; }
    public void setEdition(String edition) { this.edition = edition; }

    public String getAuxData() { return auxData; }
    public void setAuxData(String auxData) { this.auxData = auxData; }
}
