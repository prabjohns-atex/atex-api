package com.atex.desk.integration.feed.parser;

import com.atex.desk.integration.feed.WireArticle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parser for PTI (Press Trust of India) plain text format.
 * Ported from adm-starterkit PTIArticleParser.
 *
 * <p>Format: line-based text, no XML. Lines 2-3 are dimension tags,
 * first and last lines are header/footer, rest is body.
 * Headline is derived from filename.
 */
public class PTIArticleParser implements WireArticleParser {

    @Override
    public List<WireArticle> parseArticles(File file, String encoding) throws IOException {
        Charset charset = encoding != null ? Charset.forName(encoding) : Charset.defaultCharset();
        String content = Files.readString(file.toPath(), charset);

        // Remove empty lines
        content = content.replaceAll("(?m)^\\s*\n", "");

        List<String> lines = new ArrayList<>(List.of(content.split("\n")));
        if (lines.size() < 4) {
            return List.of(); // Not enough lines for header + 2 tags + footer
        }

        WireArticle article = new WireArticle();
        article.setSource("PTI");

        // Headline from filename
        String fileName = file.getName();
        if (fileName.endsWith(".txt")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        article.setHeadline(fileName);

        // Lines 2-3 (index 1-2) are dimension tags
        List<String> tags = new ArrayList<>();
        if (lines.size() > 1 && !lines.get(1).isBlank()) tags.add(lines.get(1).trim());
        if (lines.size() > 2 && !lines.get(2).isBlank()) tags.add(lines.get(2).trim());
        article.setTags(tags);

        // Remove header (line 0), tags (lines 1-2), footer (last line)
        List<String> bodyLines = new ArrayList<>(lines);
        bodyLines.removeFirst(); // header
        if (bodyLines.size() > 0) bodyLines.removeFirst(); // tag 1
        if (bodyLines.size() > 0) bodyLines.removeFirst(); // tag 2
        if (bodyLines.size() > 0) bodyLines.removeLast();  // footer

        article.setBody(String.join("\n", bodyLines));

        return List.of(article);
    }
}
