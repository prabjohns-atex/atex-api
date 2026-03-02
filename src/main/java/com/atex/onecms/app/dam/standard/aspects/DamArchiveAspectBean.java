package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.app.dam.DamContentBean;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Deprecated
public class DamArchiveAspectBean extends DamContentBean implements IDamArchiveAspectBean {

    private String lastPublication = null;
    private String lastEdition = null;
    private String lastPage = null;
    private Date lastPubdate = null;
    private String lastPagelevel = null;
    private String lastSection = null;
    private String domain = null;
    private boolean engaged;
    private String legacyid = null;
    private List<String> related = new ArrayList<>();

    public DamArchiveAspectBean() {
        setContentType("parentDocument");
    }

    @Override public String getLastPublication() { return lastPublication; }
    @Override public void setLastPublication(String lastPublication) { this.lastPublication = lastPublication; }
    @Override public String getLastEdition() { return lastEdition; }
    @Override public void setLastEdition(String lastEdition) { this.lastEdition = lastEdition; }
    @Override public String getLastPage() { return lastPage; }
    @Override public void setLastPage(String lastPage) { this.lastPage = lastPage; }
    @Override public Date getLastPubdate() { return lastPubdate; }
    @Override public void setLastPubdate(Date lastPubdate) { this.lastPubdate = lastPubdate; }
    @Override public String getLastPagelevel() { return lastPagelevel; }
    @Override public void setLastPagelevel(String lastPagelevel) { this.lastPagelevel = lastPagelevel; }
    @Override public String getLastSection() { return lastSection; }
    @Override public void setLastSection(String lastSection) { this.lastSection = lastSection; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public boolean isEngaged() { return engaged; }
    public void setEngaged(boolean engaged) { this.engaged = engaged; }
    public String getLegacyid() { return legacyid; }
    public void setLegacyid(String legacyid) { this.legacyid = legacyid; }
    public List<String> getRelated() { return related; }
    public void setRelated(List<String> related) { this.related = related; }
}

