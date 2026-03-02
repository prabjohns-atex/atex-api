package com.atex.onecms.app.dam;

import java.util.Date;

/**
 * Legacy base content bean — used by deprecated Dam*AspectBean types.
 */
@Deprecated
public class DamContentBean implements IDamBean {

    private String _type = null;
    private String objectType = null;
    private String inputTemplate = null;
    private String securityParentId = null;
    private String contentType = null;
    private String name = null;
    private Date creationdate = null;
    private int words = 0;

    public String get_type() { return _type; }
    public void set_type(String _type) { this._type = _type; }

    @Override
    public String getObjectType() { return objectType; }
    @Override
    public void setObjectType(String objectType) { this.objectType = objectType; }

    @Override
    public String getInputTemplate() { return inputTemplate; }
    @Override
    public void setInputTemplate(String inputTemplate) { this.inputTemplate = inputTemplate; }

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
        if (creationdate != null && creationdate.getTime() > 0) {
            this.creationdate = creationdate;
        }
    }

    public int getWords() { return words; }
    public void setWords(int words) { this.words = words; }
}

