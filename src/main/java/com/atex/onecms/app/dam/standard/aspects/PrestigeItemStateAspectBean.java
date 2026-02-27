package com.atex.onecms.app.dam.standard.aspects;

/**
 * Aspect bean for item state (spike/unspike).
 */
public class PrestigeItemStateAspectBean {
    public static final String ASPECT_NAME = "atex.dam.itemState";

    public enum ItemState {
        PRODUCTION,
        SPIKED
    }

    private ItemState itemState;
    private String previousSecParent;

    public ItemState getItemState() { return itemState; }
    public void setItemState(ItemState v) { this.itemState = v; }
    public String getPreviousSecParent() { return previousSecParent; }
    public void setPreviousSecParent(String v) { this.previousSecParent = v; }
}
