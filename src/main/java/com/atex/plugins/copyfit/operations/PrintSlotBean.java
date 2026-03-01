package com.atex.plugins.copyfit.operations;

public class PrintSlotBean {
    private String name = "";
    private String contentId = null;
    private int slot = 0;
    private Boolean image = false;
    private String layoutTag = "";
    private String layoutCommand = null;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }

    public String getLayoutTag() { return layoutTag; }
    public void setLayoutTag(String layoutTag) { this.layoutTag = layoutTag; }

    public Boolean getImage() { return image; }
    public void setImage(Boolean image) { this.image = image; }

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }

    public String getLayoutCommand() { return layoutCommand; }
    public void setLayoutCommand(String layoutCommand) { this.layoutCommand = layoutCommand; }
}
