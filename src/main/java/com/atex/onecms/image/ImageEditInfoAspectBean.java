package com.atex.onecms.image;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean for image editing info (crops, rotation, flip, focal point).
 * Ported from polopoly core/image-service.
 */
@AspectDefinition(value = ImageEditInfoAspectBean.ASPECT_NAME, storeWithMainAspect = true)
public class ImageEditInfoAspectBean {
    public static final String ASPECT_NAME = "atex.ImageEditInfo";

    private Map<String, CropInfo> crops = new HashMap<>();
    private List<Pixelation> pixelations = Collections.emptyList();
    private int rotation;
    private boolean flipVertical;
    private boolean flipHorizontal;
    private FocalPoint focalPoint;

    public Map<String, CropInfo> getCrops() { return crops; }
    public void setCrops(Map<String, CropInfo> crops) { this.crops = crops; }
    public List<Pixelation> getPixelations() { return pixelations; }
    public void setPixelations(List<Pixelation> pixelations) { this.pixelations = pixelations; }
    public int getRotation() { return rotation; }
    public void setRotation(int rotation) { this.rotation = rotation; }
    public boolean isFlipVertical() { return flipVertical; }
    public void setFlipVertical(boolean flipVertical) { this.flipVertical = flipVertical; }
    public boolean isFlipHorizontal() { return flipHorizontal; }
    public void setFlipHorizontal(boolean flipHorizontal) { this.flipHorizontal = flipHorizontal; }
    public FocalPoint getFocalPoint() { return focalPoint; }
    public void setFocalPoint(FocalPoint focalPoint) { this.focalPoint = focalPoint; }
}
