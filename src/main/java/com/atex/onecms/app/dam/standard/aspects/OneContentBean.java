package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;
import java.util.Map;

public class OneContentBean {

    private String _type;
    private String objectType;
    private String inputTemplate;
    private String securityParentId;
    private String contentType;
    private String name;
    private Date creationdate;
    private String author;
    private int words;
    private int chars;
    private String subject;
    private String newsId;
    private String source;
    private String section;

    public String get_type() { return _type; }
    public void set_type(String _type) { this._type = _type; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) {
        if (objectType != null && !objectType.isEmpty()) this.objectType = objectType;
    }
    public String getInputTemplate() { return inputTemplate; }
    public void setInputTemplate(String inputTemplate) {
        if (inputTemplate != null && !inputTemplate.isEmpty()) this.inputTemplate = inputTemplate;
    }
    public String getSecurityParentId() { return securityParentId; }
    public void setSecurityParentId(String securityParentId) { this.securityParentId = securityParentId; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getName() { return name; }
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
}
