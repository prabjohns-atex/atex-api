package com.atex.onecms.image;

/**
 * Pixelation (scrambling) of an area of an image.
 * Ported from polopoly core/image-service.
 */
public class Pixelation extends Rectangle {
    private int level;

    public Pixelation() { super(); }

    public Pixelation(int x, int y, int width, int height, int level) {
        super(x, y, width, height);
        this.level = level;
    }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
}
