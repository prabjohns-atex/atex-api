package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import java.util.List;

@AspectDefinition(value = PrintPageAspectBean.ASPECT_NAME)
public class PrintPageAspectBean {
    public static final String ASPECT_NAME = "atex.print.Page";

    private String pageId = "";
    private String manifest = "";
    private int docId = 1;
    private int pageSequence = 1;
    private int pageNumber = 1;
    private int printNumber = 1;
    private int width = 1;
    private int height = 1;
    private String section = "";
    private String itemName = "";
    private boolean hasPdf = false;
    private boolean isVersion = false;
    private boolean leftPage = false;
    private List<LogicalPage> pages = null;
    private String progId = "";
    private String queue = null;
    private int spikeId = 0;

    public int getPageSequence() { return pageSequence; }
    public void setPageSequence(int pageSequence) { this.pageSequence = pageSequence; }
    public boolean getHasPdf() { return hasPdf; }
    public void setHasPdf(boolean hasPdf) { this.hasPdf = hasPdf; }
    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
    public int getPrintNumber() { return printNumber; }
    public void setPrintNumber(int printNumber) { this.printNumber = printNumber; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public int getDocId() { return docId; }
    public void setDocId(int docId) { this.docId = docId; }
    public String getManifest() { return manifest; }
    public void setManifest(String manifest) { this.manifest = manifest; }
    public boolean getIsVersion() { return isVersion; }
    public void setIsVersion(boolean isVersion) { this.isVersion = isVersion; }
    public boolean isLeftPage() { return leftPage; }
    public void setLeftPage(boolean leftPage) { this.leftPage = leftPage; }
    public List<LogicalPage> getPages() { return pages; }
    public void setPages(List<LogicalPage> pages) { this.pages = pages; }
    public String getProgId() { return progId; }
    public void setProgId(String progId) { this.progId = progId; }
    public String getQueue() { return queue; }
    public void setQueue(String queue) { this.queue = queue; }
    public int getSpikeId() { return spikeId; }
    public void setSpikeId(int spikeId) { this.spikeId = spikeId; }
}

