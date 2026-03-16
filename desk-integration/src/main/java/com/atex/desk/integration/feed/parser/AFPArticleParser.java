package com.atex.desk.integration.feed.parser;

import com.atex.desk.integration.feed.WireArticle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for AFP (Agence France-Presse) NewsML XML format.
 * Ported from adm-starterkit AFPArticleParser.
 *
 * <p>XML structure: NewsEnvelope → NewsItem → NewsComponent → ContentItem → nitf → body
 */
public class AFPArticleParser extends AbstractXmlParser implements WireArticleParser {

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
            throw new IOException("Failed to parse AFP file: " + file.getName(), e);
        }
    }

    private WireArticle parseArticle(Node node) {
        WireArticle article = new WireArticle();
        List<String> tags = new ArrayList<>();

        // NewsEnvelope
        Node newsEnvelope = getChildElement(node, "NewsEnvelope");
        if (newsEnvelope != null) {
            Node newsServiceNode = getChildElement(newsEnvelope, "NewsService");
            if (newsServiceNode != null) {
                String newsServiceTag = getAttributeText(newsServiceNode, "FormalName");
                if (!newsServiceTag.isBlank()) {
                    tags.add(newsServiceTag);
                }
            }

            Node priorityNode = getChildElement(newsEnvelope, "Priority");
            if (priorityNode != null) {
                String priority = getAttributeText(priorityNode, "FormalName");
                if (!priority.isBlank()) {
                    article.setPriority(priority.trim());
                }
            }

            article.setSource("AFP");
        }

        // NewsItem
        Node newsItem = getChildElement(node, "NewsItem");
        if (newsItem != null) {
            // Identification
            Node identification = getChildElement(newsItem, "Identification");
            if (identification != null) {
                Node newsIdentifier = getChildElement(identification, "NewsIdentifier");
                if (newsIdentifier != null) {
                    String providerId = getChildText(newsIdentifier, "ProviderId");
                    article.setNewsId(providerId);
                }
            }

            // NewsComponent
            Node newsComponent = getChildElement(newsItem, "NewsComponent");
            if (newsComponent != null) {
                // NewsLines
                Node newsLines = getChildElement(newsComponent, "NewsLines");
                if (newsLines != null) {
                    String headline = getChildText(newsLines, "HeadLine");
                    String slugLine = getChildText(newsLines, "SlugLine");
                    article.setHeadline(headline);
                    if (!slugLine.isBlank()) {
                        tags.add(slugLine);
                    }
                }

                // Body via inner NewsComponent → ContentItem → DataContent → nitf → body
                Node inner = getChildElement(newsComponent, "NewsComponent");
                if (inner != null) {
                    Node contentItem = getChildElement(inner, "ContentItem");
                    if (contentItem != null) {
                        Node dataContent = getChildElement(contentItem, "DataContent");
                        if (dataContent != null) {
                            Node nitf = getChildElement(dataContent, "nitf");
                            if (nitf != null) {
                                Node body = getChildElement(nitf, "body");
                                if (body != null) {
                                    article.setBody(body.getTextContent());
                                }
                            }
                        }
                    }
                }

                // DescriptiveMetadata → keywords and location
                Node descriptiveMetadata = getChildElement(newsComponent, "DescriptiveMetadata");
                if (descriptiveMetadata != null) {
                    Node locationNode = getChildElement(descriptiveMetadata, "Location");
                    if (locationNode != null) {
                        List<Node> locationProps = getChildElements(locationNode, "Property");
                        for (Node prop : locationProps) {
                            String value = getAttributeText(prop, "Value");
                            if (!value.isBlank()) {
                                article.getLocations().add(value);
                            }
                        }
                    }

                    List<Node> propertyNodes = getChildElements(descriptiveMetadata, "Property");
                    for (Node prop : propertyNodes) {
                        String formalName = getAttributeText(prop, "FormalName");
                        String value = getAttributeText(prop, "Value");
                        if ("Keyword".equals(formalName) && !value.isBlank()) {
                            tags.add(value);
                        }
                    }
                }
            }
        }

        article.setTags(tags);
        return article;
    }
}
