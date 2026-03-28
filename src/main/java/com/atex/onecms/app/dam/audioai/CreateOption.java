package com.atex.onecms.app.dam.audioai;

import java.util.ArrayList;
import java.util.List;

/**
 * Options for creating an AudioAI content item.
 * Ported from gong/desk module-desk.
 *
 * @author mnova
 */
public class CreateOption {

    private String contentType = "atex.dam.standard.Audio";
    private String inputTemplate = "p.DamAudioAI";
    private String objectType = "audio";
    private String templateId = "atex.dam.standard.Audio";
    private String insertParentId;
    private String securityParentId = "dam.assets.production.d";
    private String webStatusId;
    private String printStatusId;
    private String taxonomyId = "p.StandardCategorization";
    private List<String> editorialTags = new ArrayList<>();

    public String getContentType() {
        return contentType;
    }

    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    public String getInputTemplate() {
        return inputTemplate;
    }

    public void setInputTemplate(final String inputTemplate) {
        this.inputTemplate = inputTemplate;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(final String objectType) {
        this.objectType = objectType;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(final String templateId) {
        this.templateId = templateId;
    }

    public String getInsertParentId() {
        return insertParentId;
    }

    public void setInsertParentId(final String insertParentId) {
        this.insertParentId = insertParentId;
    }

    public String getSecurityParentId() {
        return securityParentId;
    }

    public void setSecurityParentId(final String securityParentId) {
        this.securityParentId = securityParentId;
    }

    public String getWebStatusId() {
        return webStatusId;
    }

    public void setWebStatusId(final String webStatusId) {
        this.webStatusId = webStatusId;
    }

    public String getPrintStatusId() {
        return printStatusId;
    }

    public void setPrintStatusId(final String printStatusId) {
        this.printStatusId = printStatusId;
    }

    public String getTaxonomyId() {
        return taxonomyId;
    }

    public void setTaxonomyId(final String taxonomyId) {
        this.taxonomyId = taxonomyId;
    }

    public List<String> getEditorialTags() {
        return editorialTags;
    }

    public void setEditorialTags(final List<String> editorialTags) {
        this.editorialTags = editorialTags;
    }
}
