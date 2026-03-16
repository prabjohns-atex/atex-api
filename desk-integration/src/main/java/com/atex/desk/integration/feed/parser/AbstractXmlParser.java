package com.atex.desk.integration.feed.parser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for XML-based wire feed parsers.
 * Ported from adm-starterkit AbstractParser with DOM utility methods.
 */
public abstract class AbstractXmlParser {

    protected static final Logger LOG = Logger.getLogger(AbstractXmlParser.class.getName());

    protected Document parseXml(File file, String encoding) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream is = new FileInputStream(file)) {
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();
            return doc;
        }
    }

    protected Boolean parseBoolean(String value) {
        if (value != null) {
            return Boolean.valueOf(value.toLowerCase().trim());
        }
        return null;
    }

    protected Date parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            long ts = Long.parseLong(value);
            Calendar cal = Calendar.getInstance();
            cal.clear();
            cal.setLenient(false);
            cal.setTimeInMillis(ts);
            return cal.getTime();
        } catch (NumberFormatException e) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
            fmt.setLenient(false);
            try {
                return fmt.parse(value);
            } catch (ParseException e2) {
                LOG.log(Level.WARNING, "Cannot parse date '" + value + "': " + e2.getMessage());
            }
        }
        return null;
    }

    protected long parseDateAndTime(String value, String pattern) {
        if (value == null || value.isBlank()) return 0;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDateTime dateTime = LocalDateTime.parse(value, formatter);
            return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            LOG.log(Level.WARNING, "Cannot parse date '" + value + "': " + e.getMessage());
            return 0;
        }
    }

    protected String getChildValue(Node element, String name) {
        Node node = getChildElement(element, name);
        if (node != null) {
            Node textNode = node.getLastChild();
            if (textNode != null) {
                return textNode.getTextContent();
            }
        }
        return null;
    }

    protected String getChildText(Node node, String name) {
        String value = getChildValue(node, name);
        return value != null ? value : "";
    }

    protected Node getChildElement(Node element, String name) {
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Element.ELEMENT_NODE && node.getNodeName().equals(name)) {
                return node;
            }
        }
        return null;
    }

    protected Node getChildElement(Node element, String tag, String nameAttr) {
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Element.ELEMENT_NODE && node.getNodeName().equals(tag)) {
                if (node.getAttributes().getNamedItem("name") != null
                    && node.getAttributes().getNamedItem("name").getTextContent().equals(nameAttr)) {
                    return node;
                }
            }
        }
        return null;
    }

    protected List<Node> getChildElements(Node element, String name) {
        NodeList nodeList = element.getChildNodes();
        List<Node> list = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Element.ELEMENT_NODE && node.getNodeName().equals(name)) {
                list.add(node);
            }
        }
        return list;
    }

    protected String getAttributeValue(Node element, String name) {
        try {
            return element.getAttributes().getNamedItem(name).getNodeValue();
        } catch (Exception e) {
            return null;
        }
    }

    protected String getAttributeText(Node node, String attributeName) {
        String value = getAttributeValue(node, attributeName);
        return value != null ? value : "";
    }
}
