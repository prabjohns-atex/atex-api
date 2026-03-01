package com.atex.onecms.ws.search;

/**
 * Generic tree builder interface for converting Solr NamedList responses
 * into structured output (JSON or XML).
 *
 * @param <E> Element type (JsonElement for JSON, Element for XML)
 */
public interface ChildFactory<E> {

    E createElement(String name, String typeName);

    void appendChild(E parent, E child);

    void appendChild(E parent, String value);

    void appendChild(E parent, Number value);

    E getElement();

    String getAttribute(E element, String name);

    void setAttribute(E element, String name, String value);

    String getName(E element);

    void setName(E element, String name);
}
