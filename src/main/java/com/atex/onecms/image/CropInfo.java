package com.atex.onecms.image;

/**
 * Describes a crop rectangle for a specific image format.
 * Ported from polopoly core/image-service.
 */
public class CropInfo {
    private Rectangle cropRectangle;
    private ImageFormat imageFormat;

    public CropInfo() {}

    public CropInfo(Rectangle cropRectangle, ImageFormat imageFormat) {
        this.cropRectangle = cropRectangle;
        this.imageFormat = imageFormat;
    }

    public Rectangle getCropRectangle() { return cropRectangle; }
    public void setCropRectangle(Rectangle cropRectangle) { this.cropRectangle = cropRectangle; }
    public ImageFormat getImageFormat() { return imageFormat; }
    public void setImageFormat(ImageFormat imageFormat) { this.imageFormat = imageFormat; }
}
