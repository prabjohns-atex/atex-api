package com.atex.onecms.ws.search;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DOM-based ChildFactory for building XML representations of Solr NamedList responses.
 * Ported from polopoly/core/data-api.
 */
public class ChildFactoryXML implements ChildFactory<Element> {

    private static final Logger LOG = Logger.getLogger(ChildFactoryXML.class.getName());

    private final Document document;

    public ChildFactoryXML() {
        try {
            this.document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Failed to create XML document", e);
        }
    }

    @Override
    public Element createElement(final String name, final String typeName) {
        // Map special type names for XML compatibility
        String elementName = typeName;
        if ("facet_fields".equals(elementName)) {
            elementName = "lst";
        }
        if ("json".equals(elementName)) {
            elementName = "str";
        }
        if (elementName == null || elementName.isEmpty()) {
            elementName = "str";
        }

        Element element = document.createElement(elementName);
        if (name != null && !name.isEmpty()) {
            element.setAttribute("name", name);
        }
        return element;
    }

    @Override
    public void appendChild(final Element parent, final Element child) {
        if (parent != null && child != null) {
            parent.appendChild(child);
        }
    }

    @Override
    public void appendChild(final Element parent, final String value) {
        if (parent != null) {
            parent.appendChild(document.createTextNode(value != null ? value : ""));
        }
    }

    @Override
    public void appendChild(final Element parent, final Number value) {
        if (parent != null && value != null) {
            parent.appendChild(document.createTextNode(value.toString()));
        }
    }

    @Override
    public Element getElement() {
        return document.getDocumentElement();
    }

    @Override
    public String getAttribute(final Element element, final String name) {
        if (element != null && element.hasAttribute(name)) {
            return element.getAttribute(name);
        }
        return null;
    }

    @Override
    public void setAttribute(final Element element, final String name, final String value) {
        if (element != null && name != null) {
            element.setAttribute(name, value != null ? value : "");
        }
    }

    @Override
    public String getName(final Element element) {
        return getAttribute(element, "name");
    }

    @Override
    public void setName(final Element element, final String name) {
        setAttribute(element, "name", name);
    }

    public Document getDocument() {
        return document;
    }

    public void setDocumentElement(final Element element) {
        if (element != null) {
            document.appendChild(element);
        }
    }
}
