package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;
import java.util.Map;

import com.atex.onecms.app.dam.IDamBean;
import com.atex.onecms.app.dam.types.AceMigrationInfo;
import com.atex.onecms.app.dam.types.AceSlugInfo;

public class OneContentBean implements IDamBean, PropertyBag {

    private String _type = null;
    private String objectType = null;
    private String inputTemplate = null;
    private String securityParentId = null;
    private String contentType = null;
    private String name = null;
    private Date creationdate = null;
    private String author = null;
    private int words = 0;
    private int chars = 0;
    private String subject;
    private String newsId = null;
    private String source;
    private String section;
    private boolean markForArchive;
    private AceSlugInfo aceSlugInfo;
    private AceMigrationInfo aceMigrationInfo;
    private Map<String, Map<String, String>> propertyBag;

    public String get_type() { return _type; }
    public void set_type(String _type) { this._type = _type; }

    @Override
    public String getObjectType() { return objectType; }
    @Override
    public void setObjectType(String objectType) {
        if (objectType != null && !objectType.isEmpty()) this.objectType = objectType;
    }

    @Override
    public String getInputTemplate() { return inputTemplate; }
    @Override
    public void setInputTemplate(String inputTemplate) {
        if (inputTemplate != null && !inputTemplate.isEmpty()) this.inputTemplate = inputTemplate;
    }

    @Override
    public String getSecurityParentId() { return securityParentId; }
    @Override
    public void setSecurityParentId(String securityParentId) { this.securityParentId = securityParentId; }

    @Override
    public String getContentType() { return contentType; }
    @Override
    public void setContentType(String contentType) { this.contentType = contentType; }

    @Override
    public String getName() { return name; }
    @Override
    public void setName(String name) { this.name = name; }

    public Date getCreationdate() { return creationdate; }
    public void setCreationdate(Date creationdate) {
        if (creationdate != null && creationdate.getTime() > 0) this.creationdate = creationdate;
    }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public int getWords() { return words; }
    public void setWords(int words) { this.words = words; }
    public int getChars() { return chars; }
    public void setChars(int chars) { this.chars = chars; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getNewsId() { return newsId; }
    public void setNewsId(String newsId) { this.newsId = newsId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public boolean isMarkForArchive() { return markForArchive; }
    public void setMarkForArchive(boolean markForArchive) { this.markForArchive = markForArchive; }
    public AceSlugInfo getAceSlugInfo() { return aceSlugInfo; }
    public void setAceSlugInfo(AceSlugInfo aceSlugInfo) { this.aceSlugInfo = aceSlugInfo; }
    public AceMigrationInfo getAceMigrationInfo() { return aceMigrationInfo; }
    public void setAceMigrationInfo(AceMigrationInfo aceMigrationInfo) { this.aceMigrationInfo = aceMigrationInfo; }

    @Override
    public Map<String, Map<String, String>> getPropertyBag() { return propertyBag; }
    @Override
    public void setPropertyBag(final Map<String, Map<String, String>> propertyBag) { this.propertyBag = propertyBag; }
}
