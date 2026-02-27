package com.atex.onecms.content;

import java.util.HashMap;
import java.util.Map;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

/**
 * Bean for file references aspect.
 */
@AspectDefinition("atex.Files")
public class FilesAspectBean {
    private Map<String, ContentFileInfo> files;

    public FilesAspectBean() {
        this.files = new HashMap<>();
    }

    public Map<String, ContentFileInfo> getFiles() {
        return files;
    }

    public void setFiles(Map<String, ContentFileInfo> files) {
        this.files = files;
    }
}
