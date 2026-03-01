package com.atex.onecms.ws.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.util.NamedList;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for search service operations.
 * Handles query string parsing, response format conversion (JSON/XML),
 * and Solr error formatting.
 * <p>
 * Ported from polopoly/core/data-api SearchServiceUtil + SearchResponseSolrJ.
 */
public class SearchServiceUtil {

    private static final Logger LOG = Logger.getLogger(SearchServiceUtil.class.getName());

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    private SearchServiceUtil() {
    }

    /**
     * Parse a URL query string into a SolrQuery.
     * Handles URL decoding and multi-value parameters.
     */
    public static SolrQuery parseQueryString(final String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return new SolrQuery();
        }

        Map<String, List<String>> params = new HashMap<>();
        String qs = queryString;
        if (qs.startsWith("?")) {
            qs = qs.substring(1);
        }

        String[] pairs = qs.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key;
            String value;
            if (eq >= 0) {
                key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            } else {
                key = URLDecoder.decode(pair, StandardCharsets.UTF_8);
                value = "";
            }
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }

        // Convert to String[] map for MultiMapSolrParams
        Map<String, String[]> arrayMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            arrayMap.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }

        SolrQuery solrQuery = new SolrQuery();
        solrQuery.add(new MultiMapSolrParams(arrayMap));
        return solrQuery;
    }

    /**
     * Deduce the response type from the Solr 'wt' parameter.
     */
    public static SolrFormat deduceResponseType(final String wt) {
        if (wt == null || wt.isEmpty() || "json".equalsIgnoreCase(wt)) {
            return SolrFormat.json;
        }
        if ("xml".equalsIgnoreCase(wt)) {
            return SolrFormat.xml;
        }
        return SolrFormat.json;
    }

    /**
     * Convert a Solr NamedList response to a JSON object.
     */
    public static JsonObject toJSON(final NamedList<Object> namedList) {
        ChildFactoryJSON factory = new ChildFactoryJSON();
        JsonObject root = new JsonObject();
        factory.setRoot(root);

        if (namedList != null) {
            parseNamedList(namedList, root, factory);
        }

        return root;
    }

    /**
     * Convert a Solr NamedList response to an XML Document.
     */
    public static Document toXML(final NamedList<Object> namedList) {
        ChildFactoryXML factory = new ChildFactoryXML();
        Element root = factory.createElement("response", "response");
        factory.setDocumentElement(root);

        if (namedList != null) {
            parseNamedList(namedList, root, factory);
        }

        return factory.getDocument();
    }

    /**
     * Create a Solr-style error response.
     */
    public static ResponseEntity<?> solrError(final int code, final String message, final SolrFormat format) {
        if (format == SolrFormat.xml) {
            ChildFactoryXML factory = new ChildFactoryXML();
            Element root = factory.createElement("response", "response");
            factory.setDocumentElement(root);

            Element header = factory.createElement("responseHeader", "lst");
            Element status = factory.createElement("status", "int");
            factory.appendChild(status, Integer.toString(code));
            factory.appendChild(header, status);
            factory.appendChild(root, header);

            Element error = factory.createElement("error", "lst");
            Element msg = factory.createElement("msg", "str");
            factory.appendChild(msg, message != null ? message : "Unknown error");
            factory.appendChild(error, msg);
            Element errorCode = factory.createElement("code", "int");
            factory.appendChild(errorCode, Integer.toString(code));
            factory.appendChild(error, errorCode);
            factory.appendChild(root, error);

            return ResponseEntity.status(code)
                .contentType(MediaType.APPLICATION_XML)
                .body(documentToString(factory.getDocument()));
        }

        // JSON format
        JsonObject json = new JsonObject();
        JsonObject header = new JsonObject();
        header.addProperty("status", code);
        json.add("responseHeader", header);

        JsonObject error = new JsonObject();
        error.addProperty("msg", message != null ? message : "Unknown error");
        error.addProperty("code", code);
        json.add("error", error);

        return ResponseEntity.status(code)
            .contentType(MediaType.APPLICATION_JSON)
            .body(json.toString());
    }

    /**
     * Format a successful Solr response in the requested format.
     */
    public static ResponseEntity<?> formatResponse(final int status,
                                                    final SolrFormat format,
                                                    final NamedList<Object> response) {
        if (format == SolrFormat.xml) {
            Document doc = toXML(response);
            return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_XML)
                .body(documentToString(doc));
        }

        JsonObject json = toJSON(response);
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(json.toString());
    }

    // ---- Private helpers ----

    @SuppressWarnings("unchecked")
    private static <E> void parseNamedList(final NamedList<Object> namedList,
                                           final E parent,
                                           final ChildFactory<E> factory) {
        for (int i = 0; i < namedList.size(); i++) {
            String name = namedList.getName(i);
            Object value = namedList.getVal(i);
            createChild(name, value, parent, factory);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E> void createChild(final String name,
                                        final Object value,
                                        final E parent,
                                        final ChildFactory<E> factory) {
        if (value == null) {
            E child = factory.createElement(name, "null");
            factory.appendChild(parent, child);
            return;
        }

        if (value instanceof NamedList) {
            E child = factory.createElement(name, "lst");
            factory.appendChild(parent, child);
            parseNamedList((NamedList<Object>) value, child, factory);
            return;
        }

        if (value instanceof org.apache.solr.common.SolrDocumentList docList) {
            E child = factory.createElement(name, "result");
            factory.setAttribute(child, "numFound", Long.toString(docList.getNumFound()));
            factory.setAttribute(child, "start", Long.toString(docList.getStart()));
            if (docList.getMaxScore() != null) {
                factory.setAttribute(child, "maxScore", Float.toString(docList.getMaxScore()));
            }
            factory.appendChild(parent, child);

            for (org.apache.solr.common.SolrDocument doc : docList) {
                E docElement = factory.createElement(null, "doc");
                factory.appendChild(child, docElement);
                for (Map.Entry<String, Object> entry : doc) {
                    createChild(entry.getKey(), entry.getValue(), docElement, factory);
                }
            }
            return;
        }

        if (value instanceof Map) {
            E child = factory.createElement(name, "lst");
            factory.appendChild(parent, child);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                createChild(String.valueOf(entry.getKey()), entry.getValue(), child, factory);
            }
            return;
        }

        if (value instanceof List) {
            E child = factory.createElement(name, "arr");
            factory.appendChild(parent, child);
            for (Object item : (List<?>) value) {
                createChild(null, item, child, factory);
            }
            return;
        }

        if (value instanceof Object[]) {
            E child = factory.createElement(name, "arr");
            factory.appendChild(parent, child);
            for (Object item : (Object[]) value) {
                createChild(null, item, child, factory);
            }
            return;
        }

        // Primitive types
        if (value instanceof String str) {
            E child = factory.createElement(name, "str");
            factory.appendChild(parent, child);
            factory.appendChild(child, str);
            return;
        }
        if (value instanceof Integer intVal) {
            E child = factory.createElement(name, "int");
            factory.appendChild(parent, child);
            factory.appendChild(child, intVal);
            return;
        }
        if (value instanceof Long longVal) {
            E child = factory.createElement(name, "long");
            factory.appendChild(parent, child);
            factory.appendChild(child, longVal);
            return;
        }
        if (value instanceof Float floatVal) {
            E child = factory.createElement(name, "float");
            factory.appendChild(parent, child);
            factory.appendChild(child, floatVal);
            return;
        }
        if (value instanceof Double doubleVal) {
            E child = factory.createElement(name, "double");
            factory.appendChild(parent, child);
            factory.appendChild(child, doubleVal);
            return;
        }
        if (value instanceof Boolean boolVal) {
            E child = factory.createElement(name, "bool");
            factory.appendChild(parent, child);
            factory.appendChild(child, boolVal.toString());
            return;
        }
        if (value instanceof Date dateVal) {
            E child = factory.createElement(name, "date");
            factory.appendChild(parent, child);
            factory.appendChild(child, formatDate(dateVal));
            return;
        }

        // Fallback: toString
        E child = factory.createElement(name, "str");
        factory.appendChild(parent, child);
        factory.appendChild(child, value.toString());
    }

    private static String formatDate(final Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    private static String documentToString(final Document document) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to convert XML document to string", e);
            return "<response/>";
        }
    }
}
