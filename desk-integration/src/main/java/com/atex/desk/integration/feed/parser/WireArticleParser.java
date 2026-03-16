package com.atex.desk.integration.feed.parser;

import com.atex.desk.integration.feed.WireArticle;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Interface for wire feed article parsers.
 * Replaces the legacy {@code ITextParser} interface from adm-starterkit.
 *
 * <p>Implementations parse agency-specific file formats (NewsML, NITF, plain text, etc.)
 * into {@link WireArticle} objects. Each agency (AFP, AP, Reuters, PTI, etc.) provides
 * its own parser implementation.
 *
 * <p>Parsers are plain Java classes with no framework dependencies. They can be provided
 * by plugins or configured via {@code desk.integration.feeds.*.parser-class}.
 */
public interface WireArticleParser {

    /**
     * Parse a wire feed file into one or more articles.
     * Most feeds produce a single article per file, but some (e.g., Reuters XML batches)
     * may produce multiple.
     *
     * @param file the feed file to parse
     * @param encoding character encoding (e.g., "UTF-8")
     * @return parsed articles (never null, may be empty if file is unparseable)
     * @throws IOException if the file cannot be read
     */
    List<WireArticle> parseArticles(File file, String encoding) throws IOException;
}
