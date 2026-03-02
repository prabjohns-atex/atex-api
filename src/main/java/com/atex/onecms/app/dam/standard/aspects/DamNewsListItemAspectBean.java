package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(DamNewsListItemAspectBean.ASPECT_NAME)
public class DamNewsListItemAspectBean extends OneContentBean {

    public static final String OBJECT_TYPE = "newsListItem";
    public static final String ASPECT_NAME = "atex.dam.standard.NewslistItem";

    private long id;
    private long itemId;
    private long storyId;
    private long alternateId;
    private int itemType;
    private long docId;
    private String sid;
    private String itemName;
    private String elementName;
    private String elementTypeName;
    private ContentId contentId;
    private String lastUpdated;
    private String timeCreated;
    private int flags;
    private String position;
    private int numImages;
    private int numColumns;
    private int priority;
    private int area;
    private int depth;
    private int budgetDepth;
    private String pubDate;
    private long pubInfoId;
    private String itemPublication;
    private String itemEdition;
    private String itemZone;
    private String itemSection;
    private String itemRegion;
    private int pageNumber;
    private String pageId;
    private long profileId;
    private String auxdata;

    public DamNewsListItemAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public long getItemId() { return itemId; }
    public void setItemId(final long itemId) { this.itemId = itemId; }
    public long getStoryId() { return storyId; }
    public void setStoryId(final long storyId) { this.storyId = storyId; }
    public long getAlternateId() { return alternateId; }
    public void setAlternateId(final long alternateId) { this.alternateId = alternateId; }
    public int getItemType() { return itemType; }
    public void setItemType(final int itemType) { this.itemType = itemType; }
    public long getDocId() { return docId; }
    public void setDocId(final long docId) { this.docId = docId; }
    public String getSid() { return sid; }
    public void setSid(final String sid) { this.sid = sid; }
    public String getItemName() { return itemName; }
    public void setItemName(final String itemName) { this.itemName = itemName; }
    public String getElementName() { return elementName; }
    public void setElementName(final String elementName) { this.elementName = elementName; }
    public String getElementTypeName() { return elementTypeName; }
    public void setElementTypeName(final String elementTypeName) { this.elementTypeName = elementTypeName; }
    public ContentId getContentId() { return contentId; }
    public void setContentId(final ContentId contentId) { this.contentId = contentId; }
    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(final String lastUpdated) { this.lastUpdated = lastUpdated; }
    public String getTimeCreated() { return timeCreated; }
    public void setTimeCreated(final String timeCreated) { this.timeCreated = timeCreated; }
    public int getFlags() { return flags; }
    public void setFlags(final int flags) { this.flags = flags; }
    public String getPosition() { return position; }
    public void setPosition(final String position) { this.position = position; }
    public int getNumImages() { return numImages; }
    public void setNumImages(final int numImages) { this.numImages = numImages; }
    public int getNumColumns() { return numColumns; }
    public void setNumColumns(final int numColumns) { this.numColumns = numColumns; }
    public int getPriority() { return priority; }
    public void setPriority(final int priority) { this.priority = priority; }
    public int getArea() { return area; }
    public void setArea(final int area) { this.area = area; }
    public int getDepth() { return depth; }
    public void setDepth(final int depth) { this.depth = depth; }
    public int getBudgetDepth() { return budgetDepth; }
    public void setBudgetDepth(final int budgetDepth) { this.budgetDepth = budgetDepth; }
    public String getPubDate() { return pubDate; }
    public void setPubDate(final String pubDate) { this.pubDate = pubDate; }
    public long getPubInfoId() { return pubInfoId; }
    public void setPubInfoId(final long pubInfoId) { this.pubInfoId = pubInfoId; }
    public String getItemPublication() { return itemPublication; }
    public void setItemPublication(final String itemPublication) { this.itemPublication = itemPublication; }
    public String getItemEdition() { return itemEdition; }
    public void setItemEdition(final String itemEdition) { this.itemEdition = itemEdition; }
    public String getItemZone() { return itemZone; }
    public void setItemZone(final String itemZone) { this.itemZone = itemZone; }
    public String getItemSection() { return itemSection; }
    public void setItemSection(final String itemSection) { this.itemSection = itemSection; }
    public String getItemRegion() { return itemRegion; }
    public void setItemRegion(final String itemRegion) { this.itemRegion = itemRegion; }
    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(final int pageNumber) { this.pageNumber = pageNumber; }
    public String getPageId() { return pageId; }
    public void setPageId(final String pageId) { this.pageId = pageId; }
    public long getProfileId() { return profileId; }
    public void setProfileId(final long profileId) { this.profileId = profileId; }
    public String getAuxdata() { return auxdata; }
    public void setAuxdata(final String auxdata) { this.auxdata = auxdata; }
}

