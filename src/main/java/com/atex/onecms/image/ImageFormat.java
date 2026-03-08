package com.atex.onecms.image;

/**
 * An image format constraining images to a certain aspect ratio.
 * Ported from polopoly core/image-service.
 */
public class ImageFormat {
    private String name;
    private String label;
    private String description;
    private AspectRatio aspectRatio;

    public ImageFormat() {}

    public ImageFormat(String name, AspectRatio aspectRatio) {
        this.name = name;
        this.aspectRatio = aspectRatio;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AspectRatio getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(AspectRatio aspectRatio) { this.aspectRatio = aspectRatio; }
}
