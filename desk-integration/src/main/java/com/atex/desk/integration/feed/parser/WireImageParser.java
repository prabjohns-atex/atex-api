package com.atex.desk.integration.feed.parser;

import com.atex.desk.integration.feed.WireImage;

import java.io.File;
import java.io.IOException;

/**
 * Interface for wire feed image parsers.
 * Implementations parse agency-specific image metadata files (IPTC sidecar, XML manifest, etc.)
 * into {@link WireImage} objects containing metadata and a reference to the image file.
 */
public interface WireImageParser {

    /**
     * Parse an image feed file (metadata sidecar or the image itself) into a WireImage.
     *
     * @param file the feed file to parse
     * @param encoding character encoding for text metadata
     * @return parsed image metadata (never null)
     * @throws IOException if the file cannot be read
     */
    WireImage parseImage(File file, String encoding) throws IOException;
}
