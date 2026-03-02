package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;
import java.util.List;

import com.atex.onecms.app.dam.util.QueryField;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(DamFolderAspectBean.ASPECT_NAME)
public class DamFolderAspectBean extends OneContentBean {

    public static final String ASPECT_NAME = "atex.dam.standard.Folder";
    public static final String OBJECT_TYPE = "folder";

    public DamFolderAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
    }

    private ContentId parentId;
    private boolean smartFolder = false;
    private int printFolder = 0;
    private String cacheRef = null;
    private String sortValue;
    private String sortOrderValue;
    private String viewMode;
    private String includeTrash = "false";
    private List<String> creationDate;
    private List<String> types;
    private List<String> sources;
    private List<String> iptcCategories;
    private List<String> statusList;
    private List<String> colorCoding;
    private List<String> multiIconCoding;
    private String iconCoding;
    private List<String> webStatusList;
    private String fullText;
    private List<String> partitions;
    private List<String> publications;
    private List<String> editions;
    private String isDynamic = "false";
    private String pages;
    private List<QueryField> fields;

    public ContentId getParentId() { return parentId; }
    public void setParentId(ContentId parentId) { this.parentId = parentId; }
    public boolean isSmartFolder() { return smartFolder; }
    public void setSmartFolder(boolean smartFolder) { this.smartFolder = smartFolder; }
    public int getPrintFolder() { return printFolder; }
    public void setPrintFolder(int printFolder) { this.printFolder = printFolder; }
    public String getCacheRef() { return cacheRef; }
    public void setCacheRef(String cacheRef) { this.cacheRef = cacheRef; }
    public String getSortValue() { return sortValue; }
    public void setSortValue(String sortValue) { this.sortValue = sortValue; }
    public String getSortOrderValue() { return sortOrderValue; }
    public void setSortOrderValue(String sortOrderValue) { this.sortOrderValue = sortOrderValue; }
    public String getViewMode() { return viewMode; }
    public void setViewMode(String viewMode) { this.viewMode = viewMode; }
    public List<String> getCreationDate() { return creationDate; }
    public void setCreationDate(List<String> creationDate) { this.creationDate = creationDate; }
    public List<String> getTypes() { return types; }
    public void setTypes(List<String> types) { this.types = types; }
    public List<String> getStatusList() { return statusList; }
    public void setStatusList(List<String> statusList) { this.statusList = statusList; }
    public List<String> getColorCoding() { return colorCoding; }
    public void setColorCoding(List<String> colorCoding) { this.colorCoding = colorCoding; }
    public List<String> getMultiIconCoding() { return multiIconCoding; }
    public void setMultiIconCoding(List<String> multiIconCoding) { this.multiIconCoding = multiIconCoding; }
    public String getIconCoding() { return iconCoding; }
    public void setIconCoding(String iconCoding) { this.iconCoding = iconCoding; }
    public List<String> getWebStatusList() { return webStatusList; }
    public void setWebStatusList(List<String> webStatusList) { this.webStatusList = webStatusList; }
    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources; }
    public List<String> getIptcCategories() { return iptcCategories; }
    public void setIptcCategories(List<String> iptcCategories) { this.iptcCategories = iptcCategories; }
    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }
    public List<String> getPartitions() { return partitions; }
    public void setPartitions(List<String> partitions) { this.partitions = partitions; }
    public List<String> getPublications() { return publications; }
    public void setPublications(List<String> publications) { this.publications = publications; }
    public List<String> getEditions() { return editions; }
    public void setEditions(List<String> editions) { this.editions = editions; }
    public String getPages() { return pages; }
    public void setPages(String pages) { this.pages = pages; }
    public String getIncludeTrash() { return includeTrash; }
    public void setIncludeTrash(String includeTrash) { this.includeTrash = includeTrash; }
    public List<QueryField> getFields() { return fields; }
    public void setFields(List<QueryField> fields) { this.fields = fields; }
    public String getIsDynamic() { return isDynamic; }
    public void setIsDynamic(String isDynamic) { this.isDynamic = isDynamic; }
}

