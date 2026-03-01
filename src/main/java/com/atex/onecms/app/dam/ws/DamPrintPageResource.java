package com.atex.onecms.app.dam.ws;

import com.atex.onecms.app.dam.publication.PublicationsService;
import com.atex.onecms.app.dam.solr.SolrPrintPageService;
import com.atex.onecms.app.dam.util.StringUtils;
import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/dam/print-page")
@Tag(name = "DAM Print Page")
public class DamPrintPageResource {

    private static final Logger LOGGER = Logger.getLogger(DamPrintPageResource.class.getName());
    private final Gson gson = new Gson();

    private volatile SolrPrintPageService solrService;
    private volatile PublicationsService publicationsService;

    @GetMapping("publications/autocomplete")
    public ResponseEntity<String> getPublicationsAutocomplete(HttpServletRequest request,
                                                               @RequestParam("term") String term,
                                                               @RequestParam(value = "partition", required = false) String partition) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(term)) {
                List<String> publications;
                if ("archive".equals(partition)) {
                    publications = getSolrService().archivePublications(term);
                } else {
                    publications = getSolrService().publications(term);
                }
                return ResponseEntity.ok(gson.toJson(publications));
            } else {
                throw ContentApiException.internal("CANNOT PERFORM AUTOCOMPLETE ON PUBLICATIONS TERMS IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD PUBLICATIONS LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD PUBLICATIONS LIST", e);
        }
    }

    @GetMapping("publications")
    public ResponseEntity<String> getPublications(HttpServletRequest request,
                                                   @RequestParam(value = "partition", required = false) String partition) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            List<String> publications;
            if ("archive".equals(partition)) {
                publications = getSolrService().archivePublications();
            } else {
                publications = getSolrService().publications();
            }
            return ResponseEntity.ok(gson.toJson(publications));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD PUBLICATIONS LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD PUBLICATIONS LIST", e);
        }
    }

    @GetMapping("publications/{publication}/dates")
    public ResponseEntity<String> getPublicationDates(HttpServletRequest request,
                                                       @PathVariable("publication") String publication,
                                                       @RequestParam(value = "partition", required = false) String partition) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication)) {
                List<String> dates;
                if ("archive".equals(partition)) {
                    dates = getSolrService().archivePublicationDates(publication);
                } else {
                    dates = getSolrService().publicationDates(publication);
                }
                return ResponseEntity.ok(gson.toJson(dates));
            } else {
                throw ContentApiException.internal("PUBLICATION IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD PUBLICATIONS DATES LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD PUBLICATIONS DATES LIST", e);
        }
    }

    @GetMapping("publications/{publication}/{edition}/dates")
    public ResponseEntity<String> getPublicationEditionDates(HttpServletRequest request,
                                                              @PathVariable("publication") String publication,
                                                              @PathVariable("edition") String edition) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication) && StringUtils.notNull(edition)) {
                List<String> dates = getSolrService().publicationDates(publication, edition);
                return ResponseEntity.ok(gson.toJson(dates));
            } else {
                throw ContentApiException.internal("PUBLICATION or EDITION IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD PUBLICATIONS EDITIONS DATES LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD PUBLICATIONS EDITIONS DATES LIST", e);
        }
    }

    @GetMapping("publications/editions")
    public ResponseEntity<String> getAllEditions(HttpServletRequest request,
                                                  @RequestParam(value = "partition", required = false) String partition) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            List<String> editions;
            if ("archive".equals(partition)) {
                editions = getSolrService().archiveEditions();
            } else {
                editions = getSolrService().editions();
            }
            return ResponseEntity.ok(gson.toJson(editions));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD EDITIONS LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD EDITIONS LIST", e);
        }
    }

    @GetMapping("publications/{publication}/editions")
    public ResponseEntity<String> getEditions(HttpServletRequest request,
                                               @PathVariable("publication") String publication,
                                               @RequestParam("publicationDate") String publicationDate,
                                               @RequestParam(value = "partition", required = false) String partition) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication) && StringUtils.notNull(publicationDate)) {
                List<String> editions;
                if ("archive".equals(partition)) {
                    editions = getSolrService().archiveEditions(publication, publicationDate);
                } else {
                    editions = getSolrService().editions(publication, publicationDate);
                }
                return ResponseEntity.ok(gson.toJson(editions));
            } else {
                throw ContentApiException.internal("PUBLICATION or PUBLICATION-DATE IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD EDITIONS LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD EDITIONS LIST", e);
        }
    }

    @GetMapping("publications/{publication}/{edition}/zones")
    public ResponseEntity<String> getZones(HttpServletRequest request,
                                            @PathVariable("publication") String publication,
                                            @PathVariable("edition") String edition,
                                            @RequestParam("publicationDate") String publicationDate) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication) && StringUtils.notNull(publicationDate) && StringUtils.notNull(edition)) {
                List<String> zones = getSolrService().zones(publication, publicationDate, edition, false);
                return ResponseEntity.ok(gson.toJson(zones));
            } else {
                throw ContentApiException.internal("PUBLICATION, PUBLICATION-DATE OR EDITION IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD ZONES LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD ZONES LIST", e);
        }
    }

    @GetMapping("publications/{publication}/{edition}/sections")
    public ResponseEntity<String> getSections(HttpServletRequest request,
                                               @PathVariable("publication") String publication,
                                               @PathVariable("edition") String edition,
                                               @RequestParam("publicationDate") String publicationDate,
                                               @RequestParam(value = "partition", required = false) String partition) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication) && StringUtils.notNull(publicationDate) && StringUtils.notNull(edition)) {
                List<String> sections;
                if ("archive".equals(partition)) {
                    sections = getSolrService().archiveSections(publication, publicationDate, edition);
                } else {
                    sections = getSolrService().sections(publication, publicationDate, edition, false);
                }
                return ResponseEntity.ok(gson.toJson(sections));
            } else {
                throw ContentApiException.internal("PUBLICATION, PUBLICATION-DATE OR EDITION IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD SECTIONS LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD SECTIONS LIST", e);
        }
    }

    @GetMapping("publications/{publication}/pubeditions")
    public ResponseEntity<String> getPubEdEditions(HttpServletRequest request,
                                                    @PathVariable("publication") String publication) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication)) {
                List<String> editions = getSolrService().editions(publication);
                return ResponseEntity.ok(gson.toJson(editions));
            } else {
                throw ContentApiException.internal("PUBLICATION IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD EDITIONS LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD EDITIONS LIST", e);
        }
    }

    @GetMapping("pubeditions/{publication}/{edition}/zones")
    public ResponseEntity<String> getPubEdZones(HttpServletRequest request,
                                                 @PathVariable("publication") String publication,
                                                 @PathVariable("edition") String edition,
                                                 @RequestParam("publicationDate") String publicationDate) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication) && StringUtils.notNull(publicationDate) && StringUtils.notNull(edition)) {
                List<String> zones = getSolrService().zones(publication, publicationDate, edition, true);
                return ResponseEntity.ok(gson.toJson(zones));
            } else {
                throw ContentApiException.internal("PUBLICATION, PUBLICATION-DATE OR EDITION IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD ZONES LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD ZONES LIST", e);
        }
    }

    @GetMapping("pubeditions/{publication}/{edition}/sections")
    public ResponseEntity<String> getPubEdSections(HttpServletRequest request,
                                                    @PathVariable("publication") String publication,
                                                    @PathVariable("edition") String edition,
                                                    @RequestParam("publicationDate") String publicationDate) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication) && StringUtils.notNull(publicationDate) && StringUtils.notNull(edition)) {
                List<String> sections = getSolrService().sections(publication, publicationDate, edition, true);
                return ResponseEntity.ok(gson.toJson(sections));
            } else {
                throw ContentApiException.internal("PUBLICATION, PUBLICATION-DATE OR EDITION IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD SECTIONS LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD SECTIONS LIST", e);
        }
    }

    @GetMapping("publications/contentData")
    public ResponseEntity<String> getPublicationData(HttpServletRequest request) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            List<String> profiles = getPublicationsService().profiles();
            return ResponseEntity.ok(gson.toJson(profiles));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD PROFILE LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD PROFILE LIST", e);
        }
    }

    @GetMapping("publications/contentData/{profile}/publications")
    public ResponseEntity<String> getPublicationDataPublications(HttpServletRequest request,
                                                                  @PathVariable("profile") String profile) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(profile)) {
                List<String> publications = getPublicationsService().publications(profile);
                return ResponseEntity.ok(gson.toJson(publications));
            } else {
                throw ContentApiException.internal("PROFILE IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD PUBLICATIONS LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD PUBLICATIONS LIST", e);
        }
    }

    @GetMapping("publications/contentData/{profile}/{publication}/editions")
    public ResponseEntity<String> getPublicationDataEditions(HttpServletRequest request,
                                                              @PathVariable("profile") String profile,
                                                              @PathVariable("publication") String publication) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication)) {
                List<String> editions = getPublicationsService().editions(profile, publication);
                return ResponseEntity.ok(gson.toJson(editions));
            } else {
                throw ContentApiException.internal("PUBLICATION IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD EDITIONS LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD EDITIONS LIST", e);
        }
    }

    @GetMapping("publications/contentData/{profile}/{publication}/{edition}/zones")
    public ResponseEntity<String> getPublicationDataZones(HttpServletRequest request,
                                                           @PathVariable("profile") String profile,
                                                           @PathVariable("publication") String publication,
                                                           @PathVariable("edition") String edition) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication) && StringUtils.notNull(edition)) {
                List<String> zones = getPublicationsService().zones(profile, publication, edition);
                return ResponseEntity.ok(gson.toJson(zones));
            } else {
                throw ContentApiException.internal("PUBLICATION or EDITION IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD ZONES LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD ZONES LIST", e);
        }
    }

    @GetMapping("publications/contentData/{profile}/{publication}/{edition}/{zone}/sections")
    public ResponseEntity<String> getPublicationDataSections(HttpServletRequest request,
                                                              @PathVariable("profile") String profile,
                                                              @PathVariable("publication") String publication,
                                                              @PathVariable("edition") String edition,
                                                              @PathVariable("zone") String zone) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            if (StringUtils.notNull(publication) && StringUtils.notNull(edition) && StringUtils.notNull(zone)) {
                List<String> sections = getPublicationsService().sections(profile, publication, edition, zone);
                return ResponseEntity.ok(gson.toJson(sections));
            } else {
                throw ContentApiException.internal("PUBLICATION, EDITION or ZONE IS NULL OR EMPTY");
            }
        } catch (ContentApiException e) { throw e; }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "CANNOT LOAD SECTIONS LIST: " + e.getMessage(), e);
            throw ContentApiException.internal("CANNOT LOAD SECTIONS LIST", e);
        }
    }

    private SolrPrintPageService getSolrService() {
        if (solrService == null) {
            solrService = new SolrPrintPageService();
        }
        return solrService;
    }

    private PublicationsService getPublicationsService() {
        if (publicationsService == null) {
            publicationsService = new PublicationsService();
        }
        return publicationsService;
    }
}

