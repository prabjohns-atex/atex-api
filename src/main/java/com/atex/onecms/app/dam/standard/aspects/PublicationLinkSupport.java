package com.atex.onecms.app.dam.standard.aspects;

/**
 * Marker interface for content beans that support publication links.
 */
public interface PublicationLinkSupport {
    String getPublicationLink();
    void setPublicationLink(String link);
}
