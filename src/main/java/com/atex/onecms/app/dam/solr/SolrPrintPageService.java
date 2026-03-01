package com.atex.onecms.app.dam.solr;

import java.util.Collections;
import java.util.List;

/**
 * Solr-backed print page service for publications, editions, zones and sections.
 * Stub implementation — returns empty lists. Wire to SolrService for production use.
 */
public class SolrPrintPageService {

    public SolrPrintPageService() {}

    // Publications
    public List<String> publications() { return Collections.emptyList(); }
    public List<String> publications(String term) { return Collections.emptyList(); }
    public List<String> archivePublications() { return Collections.emptyList(); }
    public List<String> archivePublications(String term) { return Collections.emptyList(); }

    // Publication dates
    public List<String> publicationDates(String publication) { return Collections.emptyList(); }
    public List<String> publicationDates(String publication, String edition) { return Collections.emptyList(); }
    public List<String> archivePublicationDates(String publication) { return Collections.emptyList(); }

    // Editions
    public List<String> editions() { return Collections.emptyList(); }
    public List<String> editions(String publication) { return Collections.emptyList(); }
    public List<String> editions(String publication, String publicationDate) { return Collections.emptyList(); }
    public List<String> archiveEditions() { return Collections.emptyList(); }
    public List<String> archiveEditions(String publication, String publicationDate) { return Collections.emptyList(); }

    // Zones
    public List<String> zones(String publication, String publicationDate, String edition, boolean pubEdition) {
        return Collections.emptyList();
    }

    // Sections
    public List<String> sections(String publication, String publicationDate, String edition, boolean pubEdition) {
        return Collections.emptyList();
    }
    public List<String> archiveSections(String publication, String publicationDate, String edition) {
        return Collections.emptyList();
    }
}
