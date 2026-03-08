package com.atex.onecms.image;

/**
 * Represents a width:height aspect ratio.
 * Ported from polopoly core/image-service.
 */
public final class AspectRatio {
    private int width;
    private int height;

    public AspectRatio() {}

    public AspectRatio(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
}
