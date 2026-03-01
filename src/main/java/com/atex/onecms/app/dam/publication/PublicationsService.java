package com.atex.onecms.app.dam.publication;

import java.util.Collections;
import java.util.List;

/**
 * Publications content data service for print page management.
 * Stub implementation — returns empty lists.
 */
public class PublicationsService {

    public PublicationsService() {}

    public List<String> profiles() { return Collections.emptyList(); }

    public List<String> publications(String profile) { return Collections.emptyList(); }

    public List<String> editions(String profile, String publication) { return Collections.emptyList(); }

    public List<String> zones(String profile, String publication, String edition) {
        return Collections.emptyList();
    }

    public List<String> sections(String profile, String publication, String edition, String zone) {
        return Collections.emptyList();
    }
}

