package com.atex.desk.integration.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.icc.IccDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts IPTC/EXIF metadata from image files.
 * Ported from gong/desk BaseImageProcessor.extract() + adm-starterkit BaseImageProcessor.
 */
@Service
public class ImageMetadataExtractor {

    private static final Logger LOG = Logger.getLogger(ImageMetadataExtractor.class.getName());

    /**
     * Extract metadata tags from an image file.
     */
    public CustomMetadataTags extract(File imageFile) {
        CustomMetadataTags customTags = new CustomMetadataTags();
        try (InputStream is = new BufferedInputStream(new FileInputStream(imageFile))) {
            Metadata metadata = ImageMetadataReader.readMetadata(is);
            for (Directory directory : metadata.getDirectories()) {
                if (directory instanceof IccDirectory) {
                    continue;
                }
                if (directory instanceof IptcDirectory) {
                    readIptcDirectoryTags((IptcDirectory) directory, customTags);
                }
                // Store all tags by directory name
                Map<String, Object> dirTags = new HashMap<>();
                for (Tag tag : directory.getTags()) {
                    dirTags.put(tag.getTagName(), tag.getDescription());
                }
                if (!dirTags.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Map<String, ?>> tags = customTags.getTags();
                    tags.put(directory.getName(), dirTags);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to extract metadata from " + imageFile.getName(), e);
        }
        return customTags;
    }

    private void readIptcDirectoryTags(IptcDirectory directory, CustomMetadataTags tags) {
        if (directory.containsTag(IptcDirectory.TAG_BY_LINE)) {
            tags.setByline(directory.getString(IptcDirectory.TAG_BY_LINE));
        }
        if (directory.containsTag(IptcDirectory.TAG_SOURCE)) {
            tags.setSource(directory.getString(IptcDirectory.TAG_SOURCE));
        }
        if (directory.containsTag(IptcDirectory.TAG_CAPTION)) {
            tags.setDescription(directory.getString(IptcDirectory.TAG_CAPTION));
            tags.setCaption(directory.getString(IptcDirectory.TAG_CAPTION));
        }
        if (directory.containsTag(IptcDirectory.TAG_HEADLINE)) {
            tags.setHeadline(directory.getString(IptcDirectory.TAG_HEADLINE));
        }
        if (directory.containsTag(IptcDirectory.TAG_CREDIT)) {
            tags.setCredit(directory.getString(IptcDirectory.TAG_CREDIT));
        }
        if (directory.containsTag(IptcDirectory.TAG_KEYWORDS)) {
            tags.setKeywords(directory.getString(IptcDirectory.TAG_KEYWORDS));
        }
        if (directory.containsTag(IptcDirectory.TAG_CATEGORY)) {
            tags.setSubject(directory.getString(IptcDirectory.TAG_CATEGORY));
        }
        if (directory.containsTag(IptcDirectory.TAG_COPYRIGHT_NOTICE)) {
            tags.setCopyright(directory.getString(IptcDirectory.TAG_COPYRIGHT_NOTICE));
        }
        if (directory.containsTag(IptcDirectory.TAG_DATE_CREATED)) {
            tags.setDateCreated(directory.getString(IptcDirectory.TAG_DATE_CREATED));
        }
        if (directory.containsTag(IptcDirectory.TAG_COUNTRY_OR_PRIMARY_LOCATION_NAME)) {
            String location = directory.getString(IptcDirectory.TAG_COUNTRY_OR_PRIMARY_LOCATION_NAME);
            tags.setLocation(location);
        }
        if (directory.containsTag(IptcDirectory.TAG_SUB_LOCATION)) {
            String subLocation = directory.getString(IptcDirectory.TAG_SUB_LOCATION);
            if (tags.getLocation() != null) {
                tags.setLocation(tags.getLocation() + ";" + subLocation);
            } else {
                tags.setLocation(subLocation);
            }
        }
    }
}
