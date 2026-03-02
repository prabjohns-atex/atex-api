package com.atex.onecms.app.dam.standard.aspects;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OneArchiveBean extends OneContentBean {

    private String lastPublication = null;
    private String lastEdition = null;
    private String lastPage = null;
    private Date lastPubdate = null;
    private String lastPagelevel = null;
    private String lastSection = null;
    private String domain = null;
    private String legacyid = null;
    private String legacyUrl = null;
    private String archiveComment = null;
    private List<String> related = new ArrayList<>();

    public OneArchiveBean() {
        setContentType("parentDocument");
    }

    public String getLastPublication() { return lastPublication; }
    public void setLastPublication(String lastPublication) { this.lastPublication = lastPublication; }
    public String getLastEdition() { return lastEdition; }
    public void setLastEdition(String lastEdition) { this.lastEdition = lastEdition; }
    public String getLastPage() { return lastPage; }
    public void setLastPage(String lastPage) { this.lastPage = lastPage; }
    public Date getLastPubdate() { return lastPubdate; }
    public void setLastPubdate(Date lastPubdate) { this.lastPubdate = lastPubdate; }
    public String getLastPagelevel() { return lastPagelevel; }
    public void setLastPagelevel(String lastPagelevel) { this.lastPagelevel = lastPagelevel; }
    public String getLastSection() { return lastSection; }
    public void setLastSection(String lastSection) { this.lastSection = lastSection; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getLegacyid() { return legacyid; }
    public void setLegacyid(String legacyid) { this.legacyid = legacyid; }
    public String getLegacyUrl() { return legacyUrl; }
    public void setLegacyUrl(final String legacyUrl) { this.legacyUrl = legacyUrl; }
    public List<String> getRelated() { return related; }
    public void setRelated(List<String> related) { this.related = related; }
    public String getArchiveComment() { return archiveComment; }
    public void setArchiveComment(final String archiveComment) { this.archiveComment = archiveComment; }
}

