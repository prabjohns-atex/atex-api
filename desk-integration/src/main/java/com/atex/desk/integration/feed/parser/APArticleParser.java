package com.atex.desk.integration.feed.parser;

import com.atex.desk.integration.feed.WireArticle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for AP (Associated Press) IPTC NewsML-G2 XML format.
 * Ported from adm-starterkit APArticleParser.
 *
 * <p>XML structure: header → itemSet → newsItem → contentMeta/contentSet → inlineXML → nitf
 */
public class APArticleParser extends AbstractXmlParser implements WireArticleParser {

    @Override
    public List<WireArticle> parseArticles(File file, String encoding) throws IOException {
        try {
            Document doc = parseXml(file, encoding);
            Element root = doc.getDocumentElement();
            if (root == null) return List.of();

            WireArticle article = parseArticle(root);
            return article != null ? List.of(article) : List.of();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse AP file: " + file.getName(), e);
        }
    }

    private WireArticle parseArticle(Node node) {
        WireArticle article = new WireArticle();
        List<String> tags = new ArrayList<>();
        article.setSource("AP");

        // itemSet → newsItem
        Node itemSet = getChildElement(node, "itemSet");
        if (itemSet != null) {
            Node newsItem = getChildElement(itemSet, "newsItem");
            if (newsItem != null) {
                parseItemMeta(newsItem, article);
                parseContentMeta(newsItem, article, tags);
                parseContentSet(newsItem, article);
            }
        }

        article.setTags(tags);
        return article;
    }

    private void parseItemMeta(Node newsItem, WireArticle article) {
        Node itemMeta = getChildElement(newsItem, "itemMeta");
        if (itemMeta == null) return;

        String title = getChildText(itemMeta, "title");
        if (!title.isBlank()) {
            article.setNewsId(title);
        }
    }

    private void parseContentMeta(Node newsItem, WireArticle article, List<String> tags) {
        Node contentMeta = getChildElement(newsItem, "contentMeta");
        if (contentMeta == null) return;

        String urgency = getChildText(contentMeta, "urgency");
        if (!urgency.isBlank()) {
            article.setPriority(urgency.trim());
        }

        String by = getChildText(contentMeta, "by");
        if (!by.isBlank()) {
            article.setByline(by);
        }

        // Headline (first one found)
        List<Node> headlineNodes = getChildElements(contentMeta, "headline");
        for (Node hn : headlineNodes) {
            String headline = hn.getTextContent();
            if (headline != null && !headline.isBlank() && article.getHeadline() == null) {
                article.setHeadline(headline);
            }
        }

        // Creator as author
        Node creatorNode = getChildElement(contentMeta, "creator");
        if (creatorNode != null) {
            String name = getChildText(creatorNode, "name");
            if (!name.isBlank()) {
                article.setAuthor(name);
            }
        }

        // Location tags
        Node locatedNode = getChildElement(contentMeta, "located");
        if (locatedNode != null) {
            String locName = getChildText(locatedNode, "name");
            if (!locName.isBlank()) {
                article.getLocations().add(locName);
            }
            List<Node> broaderNodes = getChildElements(locatedNode, "broader");
            for (Node broader : broaderNodes) {
                String bName = getChildText(broader, "name");
                if (!bName.isBlank()) {
                    article.getLocations().add(bName);
                }
            }
        }

        // Subject/audience tags
        for (Node subjectNode : getChildElements(contentMeta, "subject")) {
            String name = getChildText(subjectNode, "name");
            if (!name.isBlank()) tags.add(name);
        }
        for (Node audienceNode : getChildElements(contentMeta, "audience")) {
            String name = getChildText(audienceNode, "name");
            if (!name.isBlank()) tags.add(name);
        }
    }

    private void parseContentSet(Node newsItem, WireArticle article) {
        Node contentSet = getChildElement(newsItem, "contentSet");
        if (contentSet == null) return;

        Node inlineXml = getChildElement(contentSet, "inlineXML");
        if (inlineXml == null) return;

        Node nitf = getChildElement(inlineXml, "nitf");
        if (nitf == null) return;

        Node body = getChildElement(nitf, "body");
        if (body == null) return;

        // body.head for headline fallback
        Node bodyHead = getChildElement(body, "body.head");
        if (bodyHead != null) {
            Node headlineNode = getChildElement(bodyHead, "headline");
            if (headlineNode != null) {
                String hl1 = getChildText(headlineNode, "hl1");
                if (!hl1.isBlank() && article.getHeadline() == null) {
                    article.setHeadline(hl1);
                }
            }
        }

        // body.content for body text
        Node bodyContent = getChildElement(body, "body.content");
        if (bodyContent != null) {
            article.setBody(bodyContent.getTextContent());
        }
    }
}
