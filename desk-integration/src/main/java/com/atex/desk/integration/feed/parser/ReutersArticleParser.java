package com.atex.desk.integration.feed.parser;

import com.atex.desk.integration.feed.WireArticle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Reuters/Thomson XML format.
 * Ported from adm-starterkit ReutersArticleParser.
 *
 * <p>XML structure: articles → article → (author, headline, cats, summary, content)
 * Supports batch format with multiple articles per file.
 */
public class ReutersArticleParser extends AbstractXmlParser implements WireArticleParser {

    @Override
    public List<WireArticle> parseArticles(File file, String encoding) throws IOException {
        try {
            Document doc = parseXml(file, encoding);
            Element root = doc.getDocumentElement();
            if (root == null) return List.of();

            List<WireArticle> articles = new ArrayList<>();
            NodeList articleNodes = root.getElementsByTagName("article");
            for (int i = 0; i < articleNodes.getLength(); i++) {
                articles.add(parseArticle(articleNodes.item(i)));
            }
            return articles;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse Reuters file: " + file.getName(), e);
        }
    }

    private WireArticle parseArticle(Node node) {
        WireArticle article = new WireArticle();
        article.setSource("Reuters");

        String author = getChildText(node, "author");
        String headline = getChildText(node, "headline");
        String cats = getChildText(node, "cats");
        String summary = getChildText(node, "summary");
        String content = getChildText(node, "content");

        if (!author.isBlank()) article.setAuthor(author);
        if (!headline.isBlank()) article.setHeadline(headline);
        if (!summary.isBlank()) article.setLead(summary);
        if (!content.isBlank()) article.setBody(content);
        if (!cats.isBlank()) {
            for (String cat : cats.split("[,;]")) {
                String tag = cat.trim();
                if (!tag.isEmpty()) article.getTags().add(tag);
            }
        }

        return article;
    }
}
