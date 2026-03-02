package com.atex.onecms.app.dam;

/**
 * Marker interface for all DAM content beans.
 */
public interface IDamBean {

    String getObjectType();
    void setObjectType(String objectType);

    String getSecurityParentId();
    void setSecurityParentId(String contentId);

    String getInputTemplate();
    void setInputTemplate(String inputTemplate);

    String getContentType();
    void setContentType(String contentType);

    String getName();
    void setName(String name);
}

