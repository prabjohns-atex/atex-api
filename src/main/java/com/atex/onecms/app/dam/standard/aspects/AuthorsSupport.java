package com.atex.onecms.app.dam.standard.aspects;

import java.util.List;
import com.atex.onecms.content.ContentId;

public interface AuthorsSupport {
    List<ContentId> getAuthors();
    void setAuthors(List<ContentId> authors);
}

