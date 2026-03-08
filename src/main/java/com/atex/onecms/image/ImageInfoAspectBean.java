package com.atex.onecms.image;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

/**
 * Bean for the image info aspect (width, height, filePath).
 * Ported from polopoly core/image-service.
 */
@AspectDefinition(value = ImageInfoAspectBean.ASPECT_NAME, storeWithMainAspect = true)
public class ImageInfoAspectBean {
    public static final String ASPECT_NAME = "atex.Image";

    private int width;
    private int height;
    private String filePath;

    public ImageInfoAspectBean() {}

    public ImageInfoAspectBean(String filePath, int width, int height) {
        this.filePath = filePath;
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
