package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.app.dam.IDamBean;
import java.util.Date;

@Deprecated
public interface IDamArchiveAspectBean extends IDamBean {
    String getLastPublication();
    void setLastPublication(String lastPublication);
    String getLastEdition();
    void setLastEdition(String lastEdition);
    String getLastPage();
    void setLastPage(String lastPage);
    Date getLastPubdate();
    void setLastPubdate(Date lastPubdate);
    String getLastPagelevel();
    void setLastPagelevel(String lastPagelevel);
    String getLastSection();
    void setLastSection(String lastSection);
}

