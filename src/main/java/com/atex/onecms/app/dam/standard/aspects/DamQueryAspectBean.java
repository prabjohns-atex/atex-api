package com.atex.onecms.app.dam.standard.aspects;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.apache.solr.client.solrj.util.ClientUtils;

import com.atex.onecms.app.dam.solr.SolrPrintPageService;
import com.atex.onecms.app.dam.util.CollectionUtils;
import com.atex.onecms.app.dam.util.QueryField;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import com.polopoly.cm.client.CMException;

@AspectDefinition(DamQueryAspectBean.ASPECT_NAME)
public class DamQueryAspectBean extends OneContentBean {

    public static final String ASPECT_NAME = "atex.dam.standard.Query";
    public static final String OBJECT_TYPE = "query";
    public static final String INPUT_TEMPLATE = "p.DamQuery";

    private static final String ALL_EDITIONS_VALUE = "ALL EDITIONS";
    private static final String SOLR_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private static final String QUERY_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final Logger LOG = Logger.getLogger(DamQueryAspectBean.class.getName());

    // Solr field constants
    public static final String itemNameField = "name_atex_desk_ts";
    public static final String inputTemplateField = "atex_desk_inputTemplate";
    public static final String content_typeField = "contentType_atex_desk_s";
    public static final String fullTextField = "atex_desk_text";
    public static final String placesField = "tag_dimension.Location_ss";
    public static final String peopleField = "tag_dimension.Person_ss";
    public static final String tagsField = "tag_dimension.Tag_ss";
    public static final String editorialTagsField = "tag_dimension.EdiorialTag_ss";
    public static final String assigneesField = "tag_dimension.sendto.Users_ss";
    public static final String creationDateField = "creationdate_atex_desk_dt";
    public static final String deadLineDateField = "deadline_atex_desk_dt";
    public static final String publicationField = "publication_atex_desk_ss";
    public static final String editionField = "edition_atex_desk_ss";
    public static final String zoneField = "zone_atex_desk_ss";
    public static final String sectionField = "section_atex_desk_ss";
    public static final String pubDateField = "pubdate_atex_desk_dts";
    public static final String pageField = "page_atex_desk_ss";
    public static final String objectTypeField = "atex_desk_objectType";
    public static final String partitionField = "tag_dimension.partition_ss";
    public static final String statusIdField = "content_status_id_s";
    public static final String statusAttributeField = "content_status_attribute_ss";
    public static final String webStatusIdField = "web_status_id_s";
    public static final String sourcesField = "source_atex_desk_s";
    public static final String iptcCategoryField = "iptc_category_atex_desk_facet";
    public static final String engagementAppTypeField = "engagement_app_type_atex_desk_sm";
    public static final String resourcesField = "resources_atex_desk_sms";
    public static final String distributionListField = "distributionList_ss";
    public static final String securityParentPageField = "page_ss";
    public static final String geoLocationField = "geo_location_srpt";
    public static final String oneCmsIdFied = "id";
    public static final String shootdateField = "shootdate_atex_desk_dts";
    public static final String insertiondateField = "creationdate_atex_desk_dts";
    public static final String slugField = "name_atex_desk_e";
    public static final String bylineField = "author_atex_desk_tms";
    public static final String headlineField = "headlines_atex_desk";
    public static final String statusField = "status_atex_desk_s";
    public static final String engagedField = "engaged_atex_desk_b";
    public static final String polopolyIdField = "polopolyId_atex_desk_t";
    public static final String onTimeField = "embargo_on_time_atex_desk_dts";
    public static final String assigneesFieldPlanning = "assignees_atex_desk_sms";
    public static final String assigneesFieldHermes = "tag_dimension.sendto.Users_ss";
    public static final String digitalpublishingtimeField = "digitalpublishingtime_atex_desk_dts";
    public static final String tvpublishingtimeField = "tvpublishingtime_atex_desk_dt";
    public static final String jobTvField = "tvjob_atex_desk_b";
    public static final String publishingtimeField = "publishingtime_atex_desk_dt";
    public static final String urgencyField = "urgency_atex_desk_sms";
    public static final String printSectionField = "printsection_atex_desk_sms";

    // Instance variables
    private String contentId;
    private String fullText;
    private String itemName;
    private boolean simpleSearch;
    private boolean budgetView;
    private boolean tvJob;
    private String timeZone;
    private String isDynamic;
    private String physicalPages = "false";
    private String resources;
    private String includeTrash;
    private String byline;
    private List<String> types;
    private List<String> assignees;
    private List<String> people;
    private List<String> places;
    private List<String> publications;
    private List<String> editions;
    private List<String> zones;
    private List<String> sections;
    private List<String> partitions;
    private List<String> tags;
    private List<String> editorialTags;
    private String pages;
    private String allArchive;
    private String headlines;
    private String isNotEngaged;
    private String isWebPublished;
    private String isEmbargoed;
    private List<String> publicationData;
    private List<String> pubDate;
    private List<String> creationDate;
    private List<String> deadlineDate;
    private List<String> insertionDate;
    private String solrQueryString;
    private String reverseQueryString;
    private List<String> print_section;
    private List<String> status;
    private String sortValue;
    private String sortOrderValue;
    private String iconCoding;
    private List<String> multiIconCoding;
    private List<String> colorCoding;
    private String slug;
    private boolean hiddenQuery;
    private String metadataQuery;
    private String viewMode;
    private String display;
    private List<String> statusList;
    private List<String> attributesList;
    private List<String> webStatusList;
    private List<String> sources;
    private List<String> iptcCategories;
    private List<String> engagementAppTypes;
    private List<String> distributionList;
    private List<QueryField> fields;
    private String tab;
    private boolean singleEdition;
    private List<String> digitalPublishingTime;
    private List<String> tvPublishingTime;
    private List<String> publishingTime;
    private String enterprise;
    private List<String> urgency;

    public DamQueryAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.setInputTemplate(INPUT_TEMPLATE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
        this.hiddenQuery = false;
    }

    // ALL getters and setters
    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }
    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public boolean isSimpleSearch() { return simpleSearch; }
    public void setSimpleSearch(boolean simpleSearch) { this.simpleSearch = simpleSearch; }
    public boolean isBudgetView() { return budgetView; }
    public void setBudgetView(boolean budgetView) { this.budgetView = budgetView; }
    public boolean isTvJob() { return tvJob; }
    public void setTvJob(boolean tvJob) { this.tvJob = tvJob; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public String getIsDynamic() { return isDynamic; }
    public void setIsDynamic(String isDynamic) { this.isDynamic = isDynamic; }
    public String getPhysicalPages() { return physicalPages; }
    public void setPhysicalPages(String physicalPages) { this.physicalPages = physicalPages; }
    public String getResources() { return resources; }
    public void setResources(String resources) { this.resources = resources; }
    public String getIncludeTrash() { return includeTrash; }
    public void setIncludeTrash(String includeTrash) { this.includeTrash = includeTrash; }
    public String getByline() { return byline; }
    public void setByline(String byline) { this.byline = byline; }
    public List<String> getTypes() { return types; }
    public void setTypes(List<String> types) { this.types = types; }
    public List<String> getAssignees() { return assignees; }
    public void setAssignees(List<String> assignees) { this.assignees = assignees; }
    public List<String> getPeople() { return people; }
    public void setPeople(List<String> people) { this.people = people; }
    public List<String> getPlaces() { return places; }
    public void setPlaces(List<String> places) { this.places = places; }
    public List<String> getPublications() { return publications; }
    public void setPublications(List<String> publications) { this.publications = publications; }
    public List<String> getEditions() { return editions; }
    public void setEditions(List<String> editions) { this.editions = editions; }
    public List<String> getZones() { return zones; }
    public void setZones(List<String> zones) { this.zones = zones; }
    public List<String> getSections() { return sections; }
    public void setSections(List<String> sections) { this.sections = sections; }
    public List<String> getPartitions() { return partitions; }
    public void setPartitions(List<String> partitions) { this.partitions = partitions; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public List<String> getEditorialTags() { return editorialTags; }
    public void setEditorialTags(List<String> editorialTags) { this.editorialTags = editorialTags; }
    public String getPages() { return pages; }
    public void setPages(String pages) { this.pages = pages; }
    public String getAllArchive() { return allArchive; }
    public void setAllArchive(String allArchive) { this.allArchive = allArchive; }
    public String getHeadlines() { return headlines; }
    public void setHeadlines(String headlines) { this.headlines = headlines; }
    public String getIsNotEngaged() { return isNotEngaged; }
    public void setIsNotEngaged(String isNotEngaged) { this.isNotEngaged = isNotEngaged; }
    public String getIsWebPublished() { return isWebPublished; }
    public void setIsWebPublished(String isWebPublished) { this.isWebPublished = isWebPublished; }
    public String getIsEmbargoed() { return isEmbargoed; }
    public void setIsEmbargoed(String isEmbargoed) { this.isEmbargoed = isEmbargoed; }
    public List<String> getPublicationData() { return publicationData; }
    public void setPublicationData(List<String> publicationData) { this.publicationData = publicationData; }
    public List<String> getPubDate() { return pubDate; }
    public void setPubDate(List<String> pubDate) { this.pubDate = pubDate; }
    public List<String> getCreationDate() { return creationDate; }
    public void setCreationDate(List<String> creationDate) { this.creationDate = creationDate; }
    public List<String> getDeadlineDate() { return deadlineDate; }
    public void setDeadlineDate(List<String> deadlineDate) { this.deadlineDate = deadlineDate; }
    public List<String> getInsertionDate() { return insertionDate; }
    public void setInsertionDate(List<String> insertionDate) { this.insertionDate = insertionDate; }
    public String getSolrQueryString() { return solrQueryString; }
    public void setSolrQueryString(String solrQueryString) { this.solrQueryString = solrQueryString; }
    public String getReverseQueryString() { return reverseQueryString; }
    public void setReverseQueryString(String reverseQueryString) { this.reverseQueryString = reverseQueryString; }
    public List<String> getPrint_section() { return print_section; }
    public void setPrint_section(List<String> print_section) { this.print_section = print_section; }
    public List<String> getStatus() { return status; }
    public void setStatus(List<String> status) { this.status = status; }
    public String getSortValue() { return sortValue; }
    public void setSortValue(String sortValue) { this.sortValue = sortValue; }
    public String getSortOrderValue() { return sortOrderValue; }
    public void setSortOrderValue(String sortOrderValue) { this.sortOrderValue = sortOrderValue; }
    public String getIconCoding() { return iconCoding; }
    public void setIconCoding(String iconCoding) { this.iconCoding = iconCoding; }
    public List<String> getMultiIconCoding() { return multiIconCoding; }
    public void setMultiIconCoding(List<String> multiIconCoding) { this.multiIconCoding = multiIconCoding; }
    public List<String> getColorCoding() { return colorCoding; }
    public void setColorCoding(List<String> colorCoding) { this.colorCoding = colorCoding; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public boolean isHiddenQuery() { return hiddenQuery; }
    public void setHiddenQuery(boolean hiddenQuery) { this.hiddenQuery = hiddenQuery; }
    public String getMetadataQuery() { return metadataQuery; }
    public void setMetadataQuery(String metadataQuery) { this.metadataQuery = metadataQuery; }
    public String getViewMode() { return viewMode; }
    public void setViewMode(String viewMode) { this.viewMode = viewMode; }
    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }
    public List<String> getStatusList() { return statusList; }
    public void setStatusList(List<String> statusList) { this.statusList = statusList; }
    public List<String> getAttributesList() { return attributesList; }
    public void setAttributesList(List<String> attributesList) { this.attributesList = attributesList; }
    public List<String> getWebStatusList() { return webStatusList; }
    public void setWebStatusList(List<String> webStatusList) { this.webStatusList = webStatusList; }
    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources; }
    public List<String> getIptcCategories() { return iptcCategories; }
    public void setIptcCategories(List<String> iptcCategories) { this.iptcCategories = iptcCategories; }
    public List<String> getEngagementAppTypes() { return engagementAppTypes; }
    public void setEngagementAppTypes(List<String> engagementAppTypes) { this.engagementAppTypes = engagementAppTypes; }
    public List<String> getDistributionList() { return distributionList; }
    public void setDistributionList(List<String> distributionList) { this.distributionList = distributionList; }
    public List<QueryField> getFields() { return fields; }
    public void setFields(List<QueryField> fields) { this.fields = fields; }
    public String getTab() { return tab; }
    public void setTab(String tab) { this.tab = tab; }
    public boolean isSingleEdition() { return singleEdition; }
    public void setSingleEdition(boolean singleEdition) { this.singleEdition = singleEdition; }
    public List<String> getDigitalPublishingTime() { return digitalPublishingTime; }
    public void setDigitalPublishingTime(List<String> digitalPublishingTime) { this.digitalPublishingTime = digitalPublishingTime; }
    public List<String> getTvPublishingTime() { return tvPublishingTime; }
    public void setTvPublishingTime(List<String> tvPublishingTime) { this.tvPublishingTime = tvPublishingTime; }
    public List<String> getPublishingTime() { return publishingTime; }
    public void setPublishingTime(List<String> publishingTime) { this.publishingTime = publishingTime; }
    public String getEnterprise() { return enterprise; }
    public void setEnterprise(String enterprise) { this.enterprise = enterprise; }
    public List<String> getUrgency() { return urgency; }
    public void setUrgency(List<String> urgency) { this.urgency = urgency; }

    // ---- Query building methods ----

    public void updateSolrQueryString(SolrPrintPageService service) throws CMException {
        String _solrQueryString = "";
        String _reverseQueryString = "";

        if (this.isSimpleSearch()) {
            _solrQueryString = "+" + fullTextField + ":(" + this.getFullText() + ")";
        } else {
            String fullTextStr = this.getFullText();
            if (fullTextStr != null && !fullTextStr.trim().isEmpty()) {
                _solrQueryString = "+" + fullTextField + ":(" + fullTextStr + ")";
            }

            String itemNameVal = this.getItemName();
            if (itemNameVal != null && !itemNameVal.trim().isEmpty()) {
                _solrQueryString += "+" + itemNameField + ":(" + itemNameVal + ")";
            }

            List<String> typesVal = this.getTypes();
            if (typesVal != null && !typesVal.isEmpty()) {
                _solrQueryString += "+" + objectTypeField + ":(" + String.join(" ", typesVal) + ")^0.000001";
            }

            String includeResources = this.getResources();
            if (includeResources != null && !includeResources.isEmpty()) {
                if (Boolean.parseBoolean(includeResources)) {
                    _solrQueryString += "+" + resourcesField + ":(*)";
                }
            }

            _solrQueryString += expandQuery(assigneesField, getAssignees()).orElse("");
            _solrQueryString += expandQuery(partitionField, getPartitions()).orElse("");
            _solrQueryString += expandQuery(iptcCategoryField, getSources()).orElse("");
            _solrQueryString += expandQuery(sourcesField, getIptcCategories()).orElse("");
            _solrQueryString += expandQuery(placesField, getPlaces()).orElse("");
            _solrQueryString += expandQuery(peopleField, getPeople()).orElse("");
            _solrQueryString += expandQuery(tagsField, getTags()).orElse("");
            _solrQueryString += expandQuery(editorialTagsField, getEditorialTags()).orElse("");

            List<QueryField> fieldsVal = this.getFields();
            if (fieldsVal != null && !fieldsVal.isEmpty()) {
                for (QueryField field : fieldsVal) {
                    if (!field.isSubQuery()) {
                        _solrQueryString += field.processQuery(service);
                    }
                }
            }

            // Deadline date
            List<String> deadlineDateVal = this.getDeadlineDate();
            if (deadlineDateVal != null && !deadlineDateVal.isEmpty()) {
                if (deadlineDateVal.size() == 2) {
                    if (deadlineDateVal.get(0).trim().isEmpty()) deadlineDateVal.set(0, "*");
                    if (deadlineDateVal.get(1).trim().isEmpty()) deadlineDateVal.set(1, "*");
                    if (!deadlineDateVal.get(0).trim().equals("*") || !deadlineDateVal.get(1).trim().equals("*"))
                        _solrQueryString += "+" + deadLineDateField + ":[" + String.join(" TO ", deadlineDateVal) + "]";
                } else if (deadlineDateVal.size() == 1) {
                    _solrQueryString += "+" + deadLineDateField + ":[" + deadlineDateVal.get(0) + "]";
                } else {
                    throw new CMException("Invalid date field content for field: deadline");
                }
            }

            // Creation date
            List<String> creationDateVal = this.getCreationDate();
            if (creationDateVal != null && !creationDateVal.isEmpty()) {
                if (creationDateVal.size() == 2) {
                    if (creationDateVal.get(0).trim().isEmpty()) creationDateVal.set(0, "*");
                    if (creationDateVal.get(1).trim().isEmpty()) creationDateVal.set(1, "*");
                    if (!creationDateVal.get(0).trim().equals("*") || !creationDateVal.get(1).trim().equals("*"))
                        _solrQueryString += "+" + creationDateField + ":[" + String.join(" TO ", creationDateVal) + "]";
                } else if (creationDateVal.size() == 1) {
                    _solrQueryString += "+" + creationDateField + ":[" + creationDateVal.get(0) + "]";
                } else {
                    throw new CMException("Invalid date field content for field: creationDate");
                }
            }

            // Markas fields
            String iconCodingVal = this.getIconCoding();
            if (iconCodingVal != null) {
                int intIconCoding = Integer.parseInt(iconCodingVal);
                if (intIconCoding > 0) {
                    _solrQueryString += "+tag_dimension.markas-iconcoding_ss:[" + iconCodingVal + " TO *]";
                }
            }
            List<String> multiIconCodingVal = this.getMultiIconCoding();
            if (multiIconCodingVal != null && !multiIconCodingVal.isEmpty()) {
                _solrQueryString += "+tag_dimension.markas-multiiconcoding_ss:(" + String.join(" ", multiIconCodingVal) + ")";
            }
            List<String> colorCodingVal = this.getColorCoding();
            if (colorCodingVal != null && !colorCodingVal.isEmpty()) {
                _solrQueryString += "+desk_atex_desk_ss:(" + String.join(" ", colorCodingVal) + ")";
            }

            if (this.metadataQuery != null && !this.metadataQuery.isEmpty()) {
                _solrQueryString += this.metadataQuery;
            }

            // Status fields
            List<String> statusListVal = this.getStatusList();
            if (statusListVal != null && !statusListVal.isEmpty()) {
                _solrQueryString += "+" + statusIdField + ":(" + String.join(" ", statusListVal) + ")";
            }

            List<String> attributesListVal = this.getAttributesList();
            if (attributesListVal != null && !attributesListVal.isEmpty()) {
                _solrQueryString += "+" + statusAttributeField + ":(" + String.join(" ", attributesListVal) + ")";
            }

            List<String> webStatusListVal = this.getWebStatusList();
            if (webStatusListVal != null && !webStatusListVal.isEmpty()) {
                _solrQueryString += "+" + webStatusIdField + ":(" + String.join(" ", webStatusListVal) + ")";
            }

            // Publication sub-query
            List<String> publication = this.getPublications();
            List<String> edition = this.getEditions();
            List<String> pubDateVal = this.getPubDate();
            List<String> zone = this.getZones();
            List<String> section = this.getSections();
            String page = this.getPages();

            String subQuery = "";
            String geoLocation = "";

            if (fieldsVal != null && !fieldsVal.isEmpty()) {
                for (QueryField field : fieldsVal) {
                    if (field.isSubQuery()) {
                        if ("GEOLOCATION".equals(field.getType())) {
                            geoLocation = field.processSubQuery(service);
                        } else {
                            subQuery += field.processSubQuery(service);
                        }
                    }
                }
            }

            if (publication != null && !publication.isEmpty()) {
                subQuery += "+" + publicationField + ":(\\\"" + String.join("\\\" \\\"", publication) + "\\\")";
            }
            if (edition != null && !edition.isEmpty()) {
                subQuery += "+" + editionField + ":(\\\"" + String.join("\\\" \\\"", edition) + "\\\")";
            }
            if (zone != null && !zone.isEmpty()) {
                subQuery += "+" + zoneField + ":(\\\"" + String.join("\\\" \\\"", zone) + "\\\")";
            }
            if (section != null && !section.isEmpty()) {
                subQuery += "+" + sectionField + ":(\\\"" + String.join("\\\" \\\"", section) + "\\\")";
            }

            if (page != null && !page.trim().isEmpty()) {
                if (page.indexOf(",") > 0) {
                    String[] pagesArr = page.split(",");
                    subQuery += "+" + pageField + ":(" + String.join(" ", pagesArr) + ")";
                } else if (page.indexOf("-") > 0) {
                    String[] pagesArr = page.split("-");
                    subQuery += "+" + pageField + ":[" + String.join(" TO ", pagesArr) + "]";
                } else {
                    subQuery += "+" + pageField + ":(" + page + ")";
                }
            }

            if (pubDateVal != null && !pubDateVal.isEmpty()) {
                if (pubDateVal.size() == 2) {
                    if (pubDateVal.get(0).trim().isEmpty()) pubDateVal.set(0, "*");
                    if (pubDateVal.get(1).trim().isEmpty()) pubDateVal.set(1, "*");
                    if (!pubDateVal.get(0).trim().equals("*") || !pubDateVal.get(1).trim().equals("*")) {
                        subQuery += "+" + pubDateField + ":[" + String.join(" TO ", pubDateVal) + "]";
                    }
                } else if (pubDateVal.size() == 1) {
                    subQuery += "+" + pubDateField + ":[" + pubDateVal.get(0) + "]";
                } else {
                    throw new CMException("Invalid date field content for field: pubDate");
                }
            }

            // Engagements
            List<String> engagements = this.getEngagementAppTypes();
            if (engagements != null && !engagements.isEmpty()) {
                if (engagements.size() == 1 && engagements.get(0).equals("-*")) {
                    _solrQueryString += "-" + engagementAppTypeField + ":(*)";
                } else if (engagements.size() == 1 && engagements.get(0).equals("*")) {
                    _solrQueryString += "+" + engagementAppTypeField + ":(*)";
                } else {
                    _solrQueryString += "+" + engagementAppTypeField + ":(\"" + String.join("\" \"", engagements) + "\")";
                }
            }

            _reverseQueryString = "+" + inputTemplateField + ":(p.DamPublEvent)" + "+_query_:\"{!child%20of='" + content_typeField + ":parentDocument'}(";
            if (!_solrQueryString.trim().isEmpty()) {
                _reverseQueryString += ClientUtils.escapeQueryChars(_solrQueryString) + ")\"";
            } else {
                _reverseQueryString += "+" + inputTemplateField + ":(p.Dam*))\"";
            }

            if (!subQuery.isEmpty()) {
                _solrQueryString = _solrQueryString + "+_query_:\"{!parent%20which='" + content_typeField + ":parentDocument'}(" + subQuery + ")\"";
                _reverseQueryString = subQuery.replaceAll("\\\\", "") + _reverseQueryString;
            }

            if (geoLocation != null && !geoLocation.isEmpty()) {
                _solrQueryString += geoLocation;
                _reverseQueryString += geoLocation;
            }
        }
        this.setSolrQueryString(_solrQueryString);
        this.setReverseQueryString(_reverseQueryString);
    }

    public void updateSolrQueryString(ContentManager contentManager, boolean isBudget) throws CMException {
        String _solrQueryString = "";
        String _reverseQueryString = "";

        String fullTextStr = this.getFullText();
        if (fullTextStr != null && !fullTextStr.trim().isEmpty()) {
            String fullTextStrTrim = fullTextStr.trim();
            if (fullTextStrTrim.contains("*") || fullTextStrTrim.contains(" ") || fullTextStrTrim.startsWith("\"")) {
                _solrQueryString = "+" + fullTextField + ":(" + fullTextStrTrim + ")";
            } else if (fullTextStrTrim.contains("onecms")) {
                _solrQueryString = "+" + oneCmsIdFied + ":\"" + fullTextStrTrim + "\"";
            } else {
                _solrQueryString = "+" + fullTextField + ":(\"" + fullTextStrTrim + "\")";
            }
        }

        if (!this.isSimpleSearch()) {
            List<String> typesVal = this.getTypes();
            if (typesVal != null && !typesVal.isEmpty()) {
                _solrQueryString += "+" + objectTypeField + ":(" + String.join(" ", typesVal) + ")^0.000001";
            }

            _solrQueryString += dateQuery(this.getCreationDate(), shootdateField);
            _solrQueryString += dateQuery(this.getInsertionDate(), insertiondateField);

            String slugVal = this.getSlug();
            if (slugVal != null && !slugVal.isEmpty()) {
                _solrQueryString += "+" + slugField + ":(" + slugVal + ")";
            }

            String bylineVal = this.getByline();
            if (bylineVal != null && !bylineVal.isEmpty()) {
                _solrQueryString += "+" + bylineField + ":(\\\"" + bylineVal + "\\\")";
            }

            String headlinesVal = this.getHeadlines();
            if (headlinesVal != null && !headlinesVal.isEmpty()) {
                _solrQueryString += "+" + headlineField + ":(\\\"" + headlinesVal + "\\\")";
            }

            List<String> printSection = this.getPrint_section();
            if (printSection != null && !printSection.isEmpty()) {
                _solrQueryString += "+" + printSectionField + ":(\"" + String.join("\" \"", printSection) + "\")";
            }

            // Status label to ID mapping
            List<String> statuses = this.getStatus();
            if (statuses != null && !statuses.isEmpty()) {
                String[] labels = new String[statuses.size()];
                int i = 0;
                for (String statusVal : statuses) {
                    labels[i] = statusVal;
                    switch (statusVal) {
                        case "WORKING": labels[i] = "0"; break;
                        case "PHOTOS SET": labels[i] = "1"; break;
                        case "READY (collection)": labels[i] = "2"; break;
                        case "LIVE (collection)": labels[i] = "3"; break;
                        case "PLANNED": labels[i] = "4"; break;
                        case "REQUESTED": labels[i] = "5"; break;
                        case "NEEDS EDIT": labels[i] = "6"; break;
                        case "TEXT OK": labels[i] = "7"; break;
                        case "UPLOADED": labels[i] = "8"; break;
                        case "READY (video)": labels[i] = "9"; break;
                        case "LIVE (video)": labels[i] = "10"; break;
                    }
                    i++;
                }
                _solrQueryString += "+" + statusField + ":(" + String.join(" ", labels) + ")";
            }

            if ("true".equals(this.getIsNotEngaged())) {
                _solrQueryString += " -" + engagedField + ":true ";
            }
            if ("true".equals(this.getIsWebPublished())) {
                _solrQueryString += "+" + polopolyIdField + ":[* TO *]";
            }
            if ("true".equals(this.getIsEmbargoed())) {
                _solrQueryString += "+" + onTimeField + ":[NOW/MINUTE TO *]";
            }

            _solrQueryString += dateQueryCurrentTimeZone(this.getPublishingTime(), publishingtimeField, getTimeZone(), isBudget);
            _solrQueryString += dateQuery(this.getDigitalPublishingTime(), digitalpublishingtimeField);
            _solrQueryString += dateQuery(this.getTvPublishingTime(), tvpublishingtimeField);

            if (this.isTvJob()) {
                _solrQueryString += "+" + jobTvField + ":(true)";
            }

            List<String> urgencyList = this.getUrgency();
            if (urgencyList != null && !urgencyList.isEmpty()) {
                _solrQueryString += "+" + urgencyField + ":(" + String.join(" ", urgencyList) + ")";
            }

            String includeResources = this.getResources();
            if (includeResources != null && !includeResources.isEmpty()) {
                if (Boolean.parseBoolean(includeResources)) {
                    _solrQueryString += "+" + resourcesField + ":(*)";
                }
            }

            List<String> assigneesVal = this.getAssignees();
            if (assigneesVal != null && !assigneesVal.isEmpty()) {
                _solrQueryString += "+(" + assigneesFieldPlanning + ":(\"" + String.join("\" \"", assigneesVal).toLowerCase() + "\")";
                _solrQueryString += " OR " + assigneesFieldHermes + ":(\"" + String.join("\" \"", assigneesVal).toLowerCase() + "\") )";
            }

            _solrQueryString += expandQuery(partitionField, getPartitions()).orElse("");
            _solrQueryString += expandQuery(sourcesField, getSources()).orElse("");
            _solrQueryString += expandQuery(placesField, getPlaces()).orElse("");
            _solrQueryString += expandQuery(peopleField, getPeople()).orElse("");

            List<String> creationDateVal = this.getCreationDate();
            if (creationDateVal != null && !creationDateVal.isEmpty()) {
                if (creationDateVal.size() == 2) {
                    if (creationDateVal.get(0).trim().isEmpty()) creationDateVal.set(0, "*");
                    if (creationDateVal.get(1).trim().isEmpty()) creationDateVal.set(1, "*");
                    if (!creationDateVal.get(0).trim().equals("*") || !creationDateVal.get(1).trim().equals("*"))
                        _solrQueryString += "+" + creationDateField + ":[" + String.join(" TO ", creationDateVal) + "]";
                } else if (creationDateVal.size() == 1) {
                    _solrQueryString += "+" + creationDateField + ":[" + creationDateVal.get(0) + "]";
                } else {
                    throw new CMException("Invalid date field content for field: creationDate");
                }
            }

            // Markas
            String iconCodingVal = this.getIconCoding();
            if (iconCodingVal != null) {
                int intIconCoding = Integer.parseInt(iconCodingVal);
                if (intIconCoding > 0) {
                    _solrQueryString += "+tag_dimension.markas-iconcoding_ss:[" + iconCodingVal + " TO *]";
                }
            }
            List<String> multiIconCodingVal = this.getMultiIconCoding();
            if (multiIconCodingVal != null && !multiIconCodingVal.isEmpty()) {
                _solrQueryString += "+tag_dimension.markas-multiiconcoding_ss:(" + String.join(" ", multiIconCodingVal) + ")";
            }
            List<String> colorCodingVal = this.getColorCoding();
            if (colorCodingVal != null && !colorCodingVal.isEmpty()) {
                _solrQueryString += "+desk_atex_desk_ss:(" + String.join(" ", colorCodingVal) + ")";
            }

            if (this.metadataQuery != null && !this.metadataQuery.isEmpty()) {
                _solrQueryString += this.metadataQuery;
            }

            List<String> statusListVal = this.getStatusList();
            if (statusListVal != null && !statusListVal.isEmpty()) {
                _solrQueryString += "+" + statusIdField + ":(" + String.join(" ", statusListVal) + ")";
            }

            List<String> webStatusListVal = this.getWebStatusList();
            if (webStatusListVal != null && !webStatusListVal.isEmpty()) {
                _solrQueryString += "+" + webStatusIdField + ":(" + String.join(" ", webStatusListVal) + ")";
            }

            // Enterprise flag
            String enterpriseQuery = "enterpriseBudget_atex_desk_b:(true)";
            String solrQuery = getEnterprise();
            if (solrQuery != null && !solrQuery.isEmpty() && solrQuery.contains(enterpriseQuery)) {
                _solrQueryString += "+" + enterpriseQuery;
            }

            // Publication sub-query
            List<String> publication = this.getPublications();
            List<String> edition = this.getEditions();
            List<String> pubDateVal = this.getPubDate();
            List<String> zone = this.getZones();
            List<String> section = this.getSections();
            String page = this.getPages();
            String subQuery = "";

            if (publication != null && !publication.isEmpty()) {
                subQuery += "+" + publicationField + ":(\\\"" + String.join("\\\" \\\"", publication) + "\\\")";
            }
            if (edition != null && !edition.isEmpty()) {
                subQuery += "+" + editionField + ":(\\\"" + ALL_EDITIONS_VALUE + "\\\" \\\"" + String.join("\\\" \\\"", edition) + "\\\")";
            }
            if (zone != null && !zone.isEmpty()) {
                subQuery += "+" + zoneField + ":(\\\"" + String.join("\\\" \\\"", zone) + "\\\")";
            }
            if (section != null && !section.isEmpty()) {
                subQuery += "+" + sectionField + ":(\\\"" + String.join("\\\" \\\"", section) + "\\\")";
            }
            if (page != null && !page.trim().isEmpty()) {
                if (page.indexOf(",") > 0) {
                    subQuery += "+" + pageField + ":(" + String.join(" ", page.split(",")) + ")";
                } else if (page.indexOf("-") > 0) {
                    subQuery += "+" + pageField + ":[" + String.join(" TO ", page.split("-")) + "]";
                } else if (page.equals("0")) {
                    _solrQueryString += "+" + pageField + ":(0)";
                } else {
                    subQuery += "+" + pageField + ":(" + page + ")";
                }
            }
            subQuery += dateQuery(pubDateVal, pubDateField);

            // Engagements
            List<String> engagements = this.getEngagementAppTypes();
            if (engagements != null && !engagements.isEmpty()) {
                if (engagements.size() == 1 && engagements.get(0).equals("-*")) {
                    _solrQueryString += "-" + engagementAppTypeField + ":(*)";
                } else if (engagements.size() == 1 && engagements.get(0).equals("*")) {
                    _solrQueryString += "+" + engagementAppTypeField + ":(*)";
                } else {
                    _solrQueryString += "+" + engagementAppTypeField + ":(\"" + String.join("\" \"", engagements) + "\")";
                }
            }

            _reverseQueryString = "+" + inputTemplateField + ":(p.DamPublEvent)" + "+_query_:\"{!child%20of='" + content_typeField + ":parentDocument'}(";
            if (!_solrQueryString.trim().isEmpty()) {
                _reverseQueryString += ClientUtils.escapeQueryChars(_solrQueryString) + ")\"";
            } else {
                _reverseQueryString += "+" + inputTemplateField + ":(p.Dam*))\"";
            }

            if (!subQuery.isEmpty()) {
                _solrQueryString = _solrQueryString + "+_query_:\"{!parent%20which='" + content_typeField + ":parentDocument'}(" + subQuery + ")\"";
                _reverseQueryString = subQuery.replaceAll("\\\\", "") + _reverseQueryString;
            }
            LOG.log(Level.WARNING, "QUERY={0}", _solrQueryString);
        }
        this.setSolrQueryString(_solrQueryString);
        this.setReverseQueryString(_reverseQueryString);
    }

    private Optional<String> expandQuery(String fieldName, List<String> values) {
        if (values != null && !values.isEmpty()) {
            return Optional.of("+" + fieldName + ":(\"" + String.join("\" \"", values) + "\")");
        }
        return Optional.empty();
    }

    private String dateQuery(List<String> dateRange, String dateField) throws CMException {
        return dateQueryCurrentTimeZone(dateRange, dateField, null, false);
    }

    private String dateQueryCurrentTimeZone(List<String> dateRange, String dateField, String zone, boolean isBudget) throws CMException {
        String query = "";
        SimpleDateFormat solrDateFormat = new SimpleDateFormat(SOLR_DATE_FORMAT);
        SimpleDateFormat queryDateFormat = new SimpleDateFormat(QUERY_DATE_FORMAT);

        if (dateRange != null && !dateRange.isEmpty() && dateRange.size() >= 2
                && dateRange.get(0) != null && dateRange.get(1) != null) {
            if (dateRange.size() == 2) {
                if (dateRange.get(0).trim().isEmpty()) dateRange.set(0, dateRange.get(1));
                if (dateRange.get(1).trim().isEmpty()) dateRange.set(1, dateRange.get(0));
                if (!dateRange.get(0).trim().isEmpty() || !dateRange.get(1).trim().isEmpty()) {
                    try {
                        String from = dateRange.get(0).trim();
                        if (from.indexOf('.') > 0) {
                            Date date = solrDateFormat.parse(from);
                            dateRange.set(0, queryDateFormat.format(date));
                        }
                        String to = dateRange.get(1).trim();
                        if (to.indexOf('T') > 0) {
                            Date date = solrDateFormat.parse(to);
                            long epoch = date.getTime();
                            Date newDate;
                            if (isBudget) {
                                newDate = new Date(epoch);
                                zone = null;
                            } else {
                                newDate = new Date(epoch + (1000 * 60 * 60 * 24) - 1);
                            }
                            dateRange.set(1, queryDateFormat.format(newDate));
                        }

                        if (zone != null) {
                            from = dateRange.get(0).trim();
                            to = dateRange.get(1).trim();
                            DateTimeFormatter queryFormat = DateTimeFormatter.ofPattern(QUERY_DATE_FORMAT);
                            LocalDateTime localDateFrom = LocalDateTime.parse(from, queryFormat);
                            LocalDateTime localDateTo = LocalDateTime.parse(to, queryFormat);
                            ZonedDateTime fromZoned = localDateFrom.atZone(ZoneId.of(zone));
                            ZonedDateTime toZoned = localDateTo.atZone(ZoneId.of(zone));
                            dateRange.set(0, queryFormat.format(fromZoned));
                            dateRange.set(1, queryFormat.format(toZoned));
                        }

                        String[] roundedDateRange = dateRange.stream()
                                .map(s -> s.concat("/MINUTE"))
                                .toArray(String[]::new);
                        String tmpQuery = "+" + dateField + ":[" + String.join(" TO ", roundedDateRange) + "]";
                        if (dateRange.get(1).equals("*")) {
                            tmpQuery = tmpQuery.replace("*/MINUTE", "*");
                        }
                        query += tmpQuery;
                    } catch (ParseException e) {
                        LOG.log(Level.WARNING, "Cannot convert date using format {0}; Error {1}",
                                new Object[] { SOLR_DATE_FORMAT, e.getMessage() });
                    }
                }
            } else {
                query += "+" + dateField + ":[" + dateRange.get(0) + "]";
            }
        } else if (dateRange != null && dateRange.size() == 1 && dateRange.get(0) != null) {
            query += "+" + dateField + ":[" + dateRange.get(0) + "]";
        }
        return query;
    }
}
