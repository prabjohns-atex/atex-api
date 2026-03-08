package com.atex.onecms.image.exif;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * Aspect for image metadata in ExifTool compatible format.
 * Ported from polopoly core/image-service.
 */
@AspectDefinition(value = MetadataTagsAspectBean.ASPECT_NAME, storeWithMainAspect = true)
public class MetadataTagsAspectBean {
    public static final String ASPECT_NAME = "atex.ImageMetadata";

    private Map<String, Map<String, ?>> tags;
    private Integer imageWidth;
    private Integer imageHeight;
    private String title;
    private String byline;
    private String description;

    public MetadataTagsAspectBean() { tags = new HashMap<>(); }

    public Map<String, Map<String, ?>> getTags() { return tags; }
    public void setTags(Map<String, Map<String, ?>> tags) { this.tags = tags; }
    public Integer getImageWidth() { return imageWidth; }
    public void setImageWidth(Integer imageWidth) { this.imageWidth = imageWidth; }
    public Integer getImageHeight() { return imageHeight; }
    public void setImageHeight(Integer imageHeight) { this.imageHeight = imageHeight; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getByline() { return byline; }
    public void setByline(String byline) { this.byline = byline; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
