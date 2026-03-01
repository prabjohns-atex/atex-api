package com.atex.onecms.app.dam.ws;

import static com.atex.common.base.Preconditions.checkNotNull;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.atex.desk.api.auth.PasswordService;
import com.atex.desk.api.config.DeskProperties;
import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.repository.AppUserRepository;
import com.atex.onecms.app.dam.DeskConfig;
import com.atex.onecms.app.dam.DeskConfigLoader;
import com.atex.onecms.app.dam.acl.AclBean;
import com.atex.onecms.app.dam.camel.CamelEngine;
import com.atex.onecms.app.dam.camel.client.CamelApiWebClient;
import com.atex.onecms.app.dam.camel.configuration.DamRoutesListBean;
import com.atex.onecms.app.dam.camel.route.Route;
import com.atex.onecms.app.dam.collection.CollectionToGallery;
import com.atex.onecms.app.dam.collection.aspect.CollectionAspect;
import com.atex.onecms.app.dam.config.DeskSystemConfiguration;
import com.atex.onecms.app.dam.distribution.list.DistributionListUtil;
import com.atex.onecms.app.dam.engagement.EngagementAspect;
import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.app.dam.image.DamImageWrapperPolicyBean;
import com.atex.onecms.app.dam.image.OneImageWrapperPolicyBean;
import com.atex.onecms.app.dam.mapping.BeanMapper;
import com.atex.onecms.app.dam.markas.DamMarkAsBean;
import com.atex.onecms.app.dam.operation.ContentOperationUtils;
import com.atex.onecms.app.dam.publevent.DamPubleventBean;
import com.atex.onecms.app.dam.publish.ContentPublisherException;
import com.atex.onecms.app.dam.publish.ContentPublisherNotFoundException;
import com.atex.onecms.app.dam.publish.DamEngagement;
import com.atex.onecms.app.dam.publish.DamPublisher;
import com.atex.onecms.app.dam.publish.DamPublisherConfiguration;
import com.atex.onecms.app.dam.publish.DamPublisherFactory;
import com.atex.onecms.app.dam.publish.DomainOverrider;
import com.atex.onecms.app.dam.publish.LockPathCreator;
import com.atex.onecms.app.dam.publish.PublicationUrlJsonParser;
import com.atex.onecms.app.dam.publish.PublishingContext;
import com.atex.onecms.app.dam.publish.config.RemotesConfiguration;
import com.atex.onecms.app.dam.publish.config.RemotesConfigurationFactory;
import com.atex.onecms.app.dam.remote.RemoteContentRefBean;
import com.atex.onecms.app.dam.remote.RemoteUtils;
import com.atex.onecms.app.dam.restrict.RestrictContentService;
import com.atex.onecms.app.dam.sendcontent.DamSendContentRequest;
import com.atex.onecms.app.dam.sendcontent.SendContentHandler;
import com.atex.onecms.app.dam.sendcontent.SendContentHandlerFinder;
import com.atex.onecms.app.dam.smartupdates.DamSmartUpdatesMetatadaBean;
import com.atex.onecms.app.dam.solr.SolrPrintPageService;
import com.atex.onecms.app.dam.solr.SolrService;
import com.atex.onecms.app.dam.solr.SolrUtils;
import com.atex.onecms.app.dam.standard.aspects.DamArticleAspectBean;
import com.atex.onecms.app.dam.standard.aspects.DamCollectionAspectBean;
import com.atex.onecms.app.dam.standard.aspects.DamImageAspectBean;
import com.atex.onecms.app.dam.standard.aspects.DamPubleventsAspectBean;
import com.atex.onecms.app.dam.standard.aspects.DamQueryAspectBean;
import com.atex.onecms.app.dam.standard.aspects.DamWireImageAspectBean;
import com.atex.onecms.app.dam.standard.aspects.OneArticleBean;
import com.atex.onecms.app.dam.standard.aspects.OneImageBean;
import com.atex.onecms.app.dam.util.CollectionAspectUtils;
import com.atex.onecms.app.dam.util.CollectionUtils;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.app.dam.util.DamMapping;
import com.atex.onecms.app.dam.util.DamUtils;
import com.atex.onecms.app.dam.util.DamWebServiceUtil;
import com.atex.onecms.app.dam.util.FileServiceUtils;
import com.atex.onecms.app.dam.util.HttpDamUtils;
import com.atex.onecms.app.dam.util.JsonUtil;
import com.atex.onecms.app.dam.util.MarkAsUtils;
import com.atex.onecms.app.dam.util.QueryField;
import com.atex.onecms.app.dam.util.SmartUpdatesMetadataUtils;
import com.atex.onecms.app.dam.util.WebServiceFailedResponse;
import com.atex.onecms.app.dam.wire.DamWireImageWrapperPolicyBean;
import com.atex.onecms.app.dam.workflow.WFStatusBean;
import com.atex.onecms.app.dam.workflow.WFStatusListBean;
import com.atex.onecms.app.user.UserDataBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentResultBuilder;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.FilesAspectBean;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.SetAliasOperation;
import com.atex.onecms.content.Status;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import com.atex.onecms.content.metadata.MetadataInfo;
import com.atex.onecms.content.repository.ContentModifiedException;
import com.atex.onecms.lockservice.LockService;
import com.atex.onecms.lockservice.LockServiceMissingException;
import com.atex.onecms.lockservice.LockServiceUtil;
import com.atex.onecms.search.solr.SolrCoreMapper;
import com.atex.onecms.search.solr.SolrCoreMapperFactory;
import com.atex.onecms.ws.service.ErrorResponseException;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.polopoly.cm.ContentIdFactory;
import com.polopoly.cm.ExternalContentId;
import com.polopoly.cm.client.CmClient;
import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.search.solr.SolrServerUrl;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Metadata;
import com.polopoly.user.server.Caller;
import com.polopoly.user.server.Group;
import com.polopoly.user.server.GroupId;
import com.polopoly.user.server.UserServer;
import com.polopoly.util.StringUtil;

/**
 * DAM Data Resource — converted from JAX-RS to Spring MVC.
 * Serves all /dam/content/* endpoints for the Desk UI.
 */
@RestController
@RequestMapping("/dam/content")
@Tag(name = "DAM", description = "DAM content operations, search, publishing and configuration")
public class DamDataResource {

    private static final Logger LOGGER = Logger.getLogger(DamDataResource.class.getName());

    private static final int PRECONDITION_FAILED = 41200;
    private static final int NOT_IMPLEMENTED     = 50100;

    private static final String GONG_SECURITY_PARENT = "PolopolyPost.d";
    private static final String ASPECT_ATEX_FILES = "atex.Files";

    // Timeout values in seconds
    private static final int CACHE_SHORT_TIMEOUT_SECONDS = 5 * 60;
    private static final int CACHE_LONG_TIMEOUT_SECONDS = 60 * 60;

    private static final CacheControl CACHE_LONG = CacheControl.maxAge(CACHE_LONG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .sMaxAge(CACHE_LONG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    private static final CacheControl CACHE_SHORT_PRIVATE = CacheControl.maxAge(CACHE_SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .sMaxAge(CACHE_SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .cachePrivate();

    private static final Cache<String, Object> OBJECT_CACHE = CacheBuilder.newBuilder()
        .expireAfterWrite(CACHE_SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .maximumSize(100)
        .build();

    public static final String SOLR_QUERY = "solr_query";
    public static final String REVERSE_QUERY = "reverse_query";

    private static final Gson GSON = new GsonBuilder().create();
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);
    private static final String EngagementAspectName = EngagementAspect.class.getAnnotation(AspectDefinition.class)
                                                                             .value()[0];

    private final ContentManager contentManager;
    private final PolicyCMServer policyCMServer;
    private final CmClient cmClient;
    private final DamPublisherFactory damPublisherFactory;
    private final DeskProperties deskProperties;
    private final AppUserRepository appUserRepository;
    private final PasswordService passwordService;

    private SolrService solrService = null;
    private SolrService solrServiceLatest = null;
    private SolrPrintPageService solrPrintPageService = null;
    private String contentApiUrl = null;

    public DamDataResource(ContentManager contentManager,
                           PolicyCMServer policyCMServer,
                           CmClient cmClient,
                           DamPublisherFactory damPublisherFactory,
                           DeskProperties deskProperties,
                           AppUserRepository appUserRepository,
                           PasswordService passwordService) {
        this.contentManager = contentManager;
        this.policyCMServer = policyCMServer;
        this.cmClient = cmClient;
        this.damPublisherFactory = damPublisherFactory;
        this.deskProperties = deskProperties;
        this.appUserRepository = appUserRepository;
        this.passwordService = passwordService;
        initSolrConfig();
    }

    private void initSolrConfig() {
        SolrUtils.setSolrServerUrl(new SolrServerUrl(deskProperties.getSolrUrl()));
        SolrUtils.setCore(deskProperties.getSolrCore());
        DeskConfig config = new DeskConfig();
        config.setSolrUrl(deskProperties.getSolrUrl());
        config.setSolrCore(deskProperties.getSolrCore());
        config.setSolrLatestCore(deskProperties.getSolrLatestCore());
        config.setApiUrl(deskProperties.getApiUrl());
        config.setDamUrl(deskProperties.getDamUrl());
        config.setPreviewUrl(deskProperties.getPreviewUrl());
        config.setCamelApiUrl(deskProperties.getCamelApiUrl());
        config.setSlackModuleUrl(deskProperties.getSlackModuleUrl());
        config.setupDamUtils();
        DeskConfigLoader.setDeskConfig(config);
    }

    private SolrService getSolrService() {
        if (solrService == null) {
            solrService = new SolrService(SolrUtils.getSolrServerUrl(), SolrUtils.getCore());
        }
        return solrService;
    }

    private SolrService getSolrServiceLatest() {
        if (solrServiceLatest == null) {
            String latestCore = getDeskConfig().getSolrLatestCore();
            if (latestCore == null || latestCore.isEmpty()) {
                latestCore = SolrUtils.getCore();
            }
            solrServiceLatest = new SolrService(SolrUtils.getSolrServerUrl(), latestCore);
        }
        return solrServiceLatest;
    }

    // ======== Configuration endpoints ========

    @GetMapping("sked/aspect")
    public String getAspectByTemplate(HttpServletRequest request,
                                      @RequestParam("template") String template) {
        DamUserContext.from(request).assertLoggedIn();
        if (StringUtil.isEmpty(template)) {
            throw ContentApiException.badRequest("TEMPLATE CANNOT BE NULL");
        }
        final InputStream stream = this.getClass().getResourceAsStream(template);
        if (stream == null) {
            throw ContentApiException.badRequest("TEMPLATE " + template + " NOT FOUND");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JsonElement json = JsonParser.parseString(sb.toString());
            return GSON.toJson(json);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot parse template " + template, e);
            throw ContentApiException.badRequest("cannot parse " + template);
        }
    }

    @PostMapping("unlock/{activityId}/{userId}/{applicationId}")
    public void unlock(HttpServletRequest request,
                       @RequestBody String json,
                       @PathVariable("activityId") String activityId,
                       @PathVariable("userId") String userId,
                       @PathVariable("applicationId") String applicationId) {
        DamUserContext ctx = DamUserContext.from(request);
        // Activity unlock — stub for now
        LOGGER.log(Level.INFO, "unlock activity: " + activityId + "/" + userId + "/" + applicationId);
    }

    @PostMapping("unlockPublish/{contentId}")
    public void unlockPublish(HttpServletRequest request,
                              @PathVariable("contentId") String contentId) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        try {
            DamPublisherConfiguration config = DamPublisherConfiguration.fetch(contentManager, ctx.getSubject());
            if (config.isUseDistributedLock()) {
                // TODO: implement lock service integration
                LOGGER.log(Level.INFO, "unlockPublish: lock service not yet implemented");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    @GetMapping("configuration/solrserverinfo")
    public ResponseEntity<String> getSolrServerUrl() {
        JsonObject json = new JsonObject();
        json.addProperty("solrUrl", deskProperties.getSolrUrl());
        json.addProperty("solrCore", deskProperties.getSolrCore());
        String latestCore = deskProperties.getSolrLatestCore();
        if (latestCore == null || latestCore.isEmpty()) {
            latestCore = deskProperties.getSolrCore();
        }
        json.addProperty("solrLatestCore", latestCore);
        return ResponseEntity.ok()
            .cacheControl(CACHE_LONG)
            .body(GSON.toJson(json));
    }

    @GetMapping("configuration/search/{type}")
    public ResponseEntity<String> getSearchComponents(HttpServletRequest request,
                                                      @PathVariable("type") String fieldName) {
        DamUserContext.from(request).assertLoggedIn();
        return terms(request, ".+", -1, DamMapping.getSolrFieldNameByFieldName(fieldName));
    }

    @GetMapping("find/{type}")
    public ResponseEntity<String> find(HttpServletRequest request,
                                       @PathVariable("type") String fieldName) {
        DamUserContext.from(request).assertLoggedIn();
        return terms(request, ".+", -1, fieldName);
    }

    @GetMapping("configuration/fields")
    public ResponseEntity<String> getFieldMapping() {
        String json = GSON.toJson(DamMapping.fieldMapping);
        return ResponseEntity.ok()
            .cacheControl(CACHE_LONG)
            .body(json);
    }

    @GetMapping("configuration/deskconfig")
    public ResponseEntity<String> getDeskConfiguration() {
        final DeskConfig config = getDeskConfig();
        return ResponseEntity.ok()
            .cacheControl(CACHE_LONG)
            .body(GSON.toJson(config));
    }

    @GetMapping("configuration/remotes")
    public ResponseEntity<String> getRemotesConfiguration(HttpServletRequest request) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        try {
            RemotesConfiguration config = RemotesConfigurationFactory.fetch(contentManager, ctx.getSubject());
            return ResponseEntity.ok()
                .cacheControl(CACHE_LONG)
                .body(GSON.toJson(config));
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal("Cannot fetch configuration", e);
        }
    }

    @GetMapping("configuration/remotes/{backendId}")
    public ResponseEntity<String> getRemoteBackendConfiguration(HttpServletRequest request,
                                                                 @PathVariable("backendId") String backendId) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        try {
            PublishingContext context = damPublisherFactory.createContext(backendId, ctx.getCaller());
            return ResponseEntity.ok()
                .cacheControl(CACHE_LONG)
                .body(GSON.toJson(context.getRemoteConfiguration()));
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal(
                String.format("cannot get backend '%s' configuration", backendId), e);
        }
    }

    // ======== Metadata endpoints ========

    @PutMapping("metadata/markas")
    public ResponseEntity<Void> updateMarkAs(HttpServletRequest request,
                                              @RequestBody String body) throws ContentModifiedException {
        DamUserContext ctx = DamUserContext.from(request);
        Subject callerSubject = ctx.assertLoggedIn().getSubject();

        DamMarkAsBean bean = GSON.fromJson(body, DamMarkAsBean.class);
        if (bean == null || bean.getId() == null) {
            throw ContentApiException.badRequest("Invalid mark-as request");
        }

        for (String id : bean.getId()) {
            ContentId contentId = IdUtil.fromString(id);

            ContentVersionId latestVersion = contentManager.resolve(contentId, callerSubject);
            if (latestVersion == null) {
                throw ContentApiException.notFound(contentId);
            }

            ContentResult<Object> cr = contentManager.get(latestVersion, null, Object.class, null, callerSubject);
            Status status = cr.getStatus();
            if (status.isError()) {
                throw ContentApiException.error(
                    "cannot access " + IdUtil.toIdString(contentId),
                    status.getDetailCode(), status.getHttpCode());
            }

            InsertionInfoAspectBean insertionInfoAspect = cr.getContent()
                .getAspectData(InsertionInfoAspectBean.ASPECT_NAME);

            boolean hasPermission = false;
            if (insertionInfoAspect != null) {
                ContentId secParentId = insertionInfoAspect.getSecurityParentId();
                hasPermission = ctx.havePermission(
                    com.polopoly.cm.ContentIdFactory.createContentId("0.0"), "WRITE", true);
            }

            if (!hasPermission) {
                LOGGER.log(Level.FINE, "CONTENT [ " + bean.getId() + " ] HAVE NOT RIGHT PERMISSIONS");
                throw ContentApiException.forbidden("Wrong permissions");
            }

            MetadataInfo info = cr.getContent().getAspectData(MarkAsUtils.ATEX_METADATA);
            String dimensionId = MarkAsUtils.getDimensionId(bean.getField());
            String value = bean.getValue();

            if (info != null) {
                if ("0".equals(value)) {
                    List<Dimension> dimensions = info.getMetadata().getDimensions();
                    boolean found = false;
                    for (Dimension currDimension : dimensions) {
                        if (currDimension.getId().equals(dimensionId)) {
                            dimensions.remove(currDimension);
                            info.getMetadata().setDimensions(dimensions);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return ResponseEntity.ok().build();
                    }
                } else {
                    Dimension dimension = info.getMetadata().getDimensionById(dimensionId);
                    if (dimension != null) {
                        MarkAsUtils.updateDimension(dimension, bean.getValue());
                    } else {
                        dimension = MarkAsUtils.createDimension(dimensionId, bean.getValue());
                        info.getMetadata().addDimension(dimension);
                    }
                }
            } else if (StringUtil.notEmpty(value)) {
                info = new MetadataInfo();
                Metadata metadata = new Metadata();
                Dimension dimension = MarkAsUtils.createDimension(dimensionId, bean.getValue());
                metadata.addDimension(dimension);
                info.setMetadata(metadata);
            }

            ContentWriteBuilder<Object> cwb = new ContentWriteBuilder<>();
            cwb.mainAspect(cr.getContent().getContentAspect());
            cwb.origin(cr.getContent().getId());
            cwb.type(cr.getContent().getContentDataType());
            cwb.aspects(cr.getContent().getAspects());
            cwb.aspect(MarkAsUtils.ATEX_METADATA, info);

            ContentWrite<Object> update = cwb.buildUpdate();
            contentManager.update(contentId, update, callerSubject);
        }

        return ResponseEntity.ok().build();
    }

    @PutMapping("smartupdate/metadata")
    public void smartUpdate(HttpServletRequest request,
                            @RequestBody String body) throws ContentModifiedException {
        DamUserContext ctx = DamUserContext.from(request);
        Subject callerSubject = ctx.assertLoggedIn().getSubject();

        DamSmartUpdatesMetatadaBean bean = GSON.fromJson(body, DamSmartUpdatesMetatadaBean.class);
        if (bean == null || bean.getId() == null) {
            throw ContentApiException.badRequest("Invalid smart update request");
        }

        for (String id : bean.getId()) {
            ContentId contentId = IdUtil.fromString(id);
            ContentVersionId latestVersion = contentManager.resolve(contentId, callerSubject);
            ContentResult<Object> cr = contentManager.get(latestVersion, null, Object.class, null, callerSubject);

            if (cr != null) {
                MetadataInfo info = cr.getContent().getAspectData(SmartUpdatesMetadataUtils.ATEX_METADATA);

                if (info != null) {
                    if (info.getMetadata() != null) {
                        Dimension dimension = info.getMetadata().getDimensionById(bean.getField());
                        if (dimension != null) {
                            SmartUpdatesMetadataUtils.updateDimension(dimension, bean.getField(), bean.getValue());
                        } else {
                            dimension = SmartUpdatesMetadataUtils.createDimension(bean.getField(), bean.getValue());
                            info.getMetadata().addDimension(dimension);
                        }
                    } else {
                        info.setMetadata(SmartUpdatesMetadataUtils.createMetadata(bean.getField(), bean.getValue()));
                    }
                } else {
                    info = SmartUpdatesMetadataUtils.createMetadataInfo(bean.getField(), bean.getValue());
                }

                ContentWriteBuilder<Object> cwb = new ContentWriteBuilder<>();
                cwb.origin(cr.getContent().getId());
                cwb.type(cr.getContent().getContentDataType());
                cwb.aspects(cr.getContent().getAspects());
                cwb.mainAspect(cr.getContent().getContentAspect());
                cwb.aspect(SmartUpdatesMetadataUtils.ATEX_METADATA, info);

                ContentWrite<Object> update = cwb.buildUpdate();
                contentManager.update(contentId, update, callerSubject);
            } else {
                LOGGER.log(Level.SEVERE, "CONTENT [ " + bean.getId() + " ] DOESN'T EXIST");
            }
        }
    }

    // ======== Content operations ========

    @PutMapping("duplicate")
    public ResponseEntity<String> duplicate(HttpServletRequest request,
                                            @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        Subject subject = ctx.getSubject();

        JsonObject json = parseAsJsonObject(body);
        if (json.has("entries")) {
            JsonArray entries = json.get("entries").getAsJsonArray();
            if (entries != null && entries.size() > 0) {
                ContentOperationUtils utils = ContentOperationUtils.getInstance();
                utils.configure(contentManager, null);
                List<ContentId> contentIds = utils.duplicate(utils.extract(entries), subject, ctx.getCaller().getLoginName());
                JsonArray duplicated = JsonUtil.toJsonArray(utils.extract(contentIds));
                return ResponseEntity.ok(GSON.toJson(duplicated));
            } else {
                throw ContentApiException.badRequest("CONTENT IDS LIST IS EMPTY");
            }
        } else {
            throw ContentApiException.badRequest("CONTENT ID TO DUPLICATE CANNOT BE NULL");
        }
    }

    @PostMapping("editionContent")
    public ResponseEntity<String> editionAll(HttpServletRequest request,
                                              @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        Subject subject = ctx.getSubject();

        JsonObject json = parseAsJsonObject(body);
        JsonObject imageMap = new JsonObject();
        if (json.has("imageMap")) {
            imageMap = json.get("imageMap").getAsJsonObject();
        }
        if (json.has("entries")) {
            JsonArray entries = json.get("entries").getAsJsonArray();
            if (entries != null && entries.size() > 0) {
                ContentOperationUtils utils = ContentOperationUtils.getInstance();
                utils.configure(contentManager, null);
                Map<ContentId, ContentId> contentIds = utils.copyContent(utils.extract(entries), utils.extract(imageMap), subject, ctx.getCaller().getLoginName());
                Map<String, String> map = utils.extract(contentIds);
                JsonObject duplicated = new JsonObject();
                map.forEach(duplicated::addProperty);
                JsonObject response = new JsonObject();
                response.add("duplicated", duplicated);
                return ResponseEntity.ok(GSON.toJson(response));
            } else {
                throw ContentApiException.badRequest("CONTENT IDS LIST IS EMPTY");
            }
        } else {
            throw ContentApiException.badRequest("CONTENT ID TO DUPLICATE CANNOT BE NULL");
        }
    }

    @PostMapping("copyContent")
    public ResponseEntity<String> duplicateAll(HttpServletRequest request,
                                                @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        Subject subject = ctx.getSubject();

        JsonObject json = parseAsJsonObject(body);
        if (json.has("entries")) {
            JsonArray entries = json.get("entries").getAsJsonArray();
            if (entries != null && entries.size() > 0) {
                ContentOperationUtils utils = ContentOperationUtils.getInstance();
                utils.configure(contentManager, null);
                Map<ContentId, ContentId> contentIds = utils.copyContent(utils.extract(entries), null, subject, ctx.getCaller().getLoginName());
                Map<String, String> map = utils.extract(contentIds);
                JsonObject duplicated = new JsonObject();
                map.forEach(duplicated::addProperty);
                JsonObject response = new JsonObject();
                response.add("duplicated", duplicated);
                return ResponseEntity.ok(GSON.toJson(response));
            } else {
                throw ContentApiException.badRequest("CONTENT IDS LIST IS EMPTY");
            }
        } else {
            throw ContentApiException.badRequest("CONTENT ID TO DUPLICATE CANNOT BE NULL");
        }
    }

    @GetMapping("copy/contentid/{contentId}")
    public ResponseEntity<String> copy(HttpServletRequest request,
                                        @PathVariable("contentId") String contentId,
                                        @RequestParam("backend") String backendId) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        PublishingContext context = damPublisherFactory.createContext(backendId, ctx.getCaller());

        try {
            ContentResult<?> cr = new RemoteUtils(context.getRemoteConfiguration())
                .copyRemoteImage(ctx.getSubject(), contentManager, contentId, null);
            if (cr == null) {
                throw ContentApiException.badRequest("Cannot get " + contentId);
            }
            return ResponseEntity.ok(GSON.toJson(cr));
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            throw ContentApiException.internal("Cannot get " + contentId, e);
        }
    }

    @GetMapping(value = "proxy", produces = MediaType.TEXT_PLAIN_VALUE)
    public String proxy(HttpServletRequest request,
                        @RequestParam("url") String wsUrl,
                        @RequestParam(value = "auth", defaultValue = "false") String auth,
                        @RequestParam("backend") String backendId) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        LOGGER.log(Level.INFO, "proxy callRemoteWs GET " + wsUrl);
        try {
            PublishingContext context = damPublisherFactory.createContext(backendId, ctx.getCaller());
            return new RemoteUtils(context.getRemoteConfiguration())
                .callRemoteWs("GET", wsUrl, Boolean.parseBoolean(auth));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "proxy error: " + e.getMessage(), e);
            throw ContentApiException.internal("proxy error: " + e.getMessage(), e);
        }
    }

    @PostMapping("notify-tag")
    public ResponseEntity<String> notifyTag(HttpServletRequest request,
                                             @RequestBody String body) {
        DamUserContext.from(request).assertLoggedIn();
        JsonObject json = parseAsJsonObject(body);
        LOGGER.log(Level.INFO, "notifyTag: " + json);
        return ResponseEntity.ok(GSON.toJson(json));
    }

    @Hidden
    @PostMapping("create-page")
    public ResponseEntity<String> createPage(HttpServletRequest request,
                                              @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        // Page creation requires deep PolicyCMServer integration — stub for now
        JsonObject json = parseAsJsonObject(body);
        LOGGER.log(Level.INFO, "createPage: " + json);
        throw ContentApiException.error("Page creation not yet implemented", NOT_IMPLEMENTED, 501);
    }

    @Hidden
    @PutMapping("sendcontent")
    public ResponseEntity<Void> sendContent(HttpServletRequest request,
                                             @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        // Send content stub — requires CamelEngine and file service integration
        LOGGER.log(Level.INFO, "sendcontent: stub");
        return ResponseEntity.ok().build();
    }

    @Hidden
    @PostMapping("assigncontent")
    public ResponseEntity<Void> assignContent(HttpServletRequest request,
                                               @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        // Assign content stub
        LOGGER.log(Level.INFO, "assigncontent: stub");
        return ResponseEntity.ok().build();
    }

    // ======== File endpoint ========

    @GetMapping("file")
    public ResponseEntity<byte[]> file(HttpServletRequest request,
                                        @RequestParam("file") String fileRef,
                                        @RequestHeader(value = "Range", required = false) String range) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        String authToken = ctx.getAuthToken();

        int lastPeriodPos = fileRef.lastIndexOf('.');
        String extension = null;
        if (lastPeriodPos > 0) {
            extension = fileRef.substring(lastPeriodPos + 1).toLowerCase();
            fileRef = fileRef.substring(0, lastPeriodPos);
        }

        String url = getContentApiUrl() + "/" + fileRef;
        try {
            ByteArrayOutputStream out = HttpDamUtils.readBinaryContentFromURL(
                url, "application/octet-stream", authToken);
            byte[] bytes = out.toByteArray();

            // PDF response
            if (bytes.length >= 10) {
                String header = new String(bytes, 0, Math.min(10, bytes.length), StandardCharsets.US_ASCII);
                if (header.startsWith("%PDF")) {
                    return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=temp.pdf")
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length))
                        .body(bytes);
                }
            }

            // Video mp4 response
            if ("mp4".equals(extension)) {
                if (range != null) {
                    String[] ranges = range.split("=")[1].split("-");
                    int from = Integer.parseInt(ranges[0]);
                    int to = (ranges.length == 2) ? Integer.parseInt(ranges[1]) : bytes.length - 1;
                    String responseRange = String.format("bytes %d-%d/%d", from, to, bytes.length);
                    byte[] subArray = Arrays.copyOfRange(bytes, from, to + 1);
                    ResponseEntity.BodyBuilder builder = (to < bytes.length - 1)
                        ? ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        : ResponseEntity.ok();
                    return builder
                        .header("Accept-Ranges", "bytes")
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(subArray.length))
                        .header(HttpHeaders.CONTENT_RANGE, responseRange)
                        .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                        .body(subArray);
                } else {
                    return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length))
                        .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                        .body(bytes);
                }
            }

            // Default
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                .body(bytes);
        } catch (Exception e) {
            throw ContentApiException.internal("Cannot read file: " + fileRef, e);
        }
    }

    // ======== Search endpoints ========

    @PostMapping("solrquery")
    public String solrquery(HttpServletRequest request,
                            @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        try {
            DamQueryAspectBean queryBean = GSON.fromJson(body, DamQueryAspectBean.class);
            if (queryBean == null) {
                throw ContentApiException.badRequest("Invalid query");
            }

            List<QueryField> fields = queryBean.getFields();
            if (CollectionUtils.notNull(fields)) {
                for (QueryField field : fields) {
                    if (isUserAssignmentField(field)) {
                        List<String> assignees = field.getValues();
                        if (CollectionUtils.notNull(assignees)) {
                            int index = assignees.indexOf("%%CURRENT-USER%%");
                            if (index > -1) {
                                assignees.remove(index);
                                assignees.add(ctx.getCaller().getLoginName());
                            }
                        }
                    } else if ("content_status_id_s".equals(field.getKey())) {
                        List<String> values = field.getValues();
                        if (values != null && !values.isEmpty()) {
                            Map<String, List<String>> collapsedStatusMap = buildCollapsedStatusMap();
                            List<String> newValues = new ArrayList<>();
                            for (String currentValue : values) {
                                if (!newValues.contains(currentValue)) {
                                    newValues.add(currentValue);
                                }
                                if (collapsedStatusMap.containsKey(currentValue)) {
                                    for (String cv : collapsedStatusMap.get(currentValue)) {
                                        if (!newValues.contains(cv)) {
                                            newValues.add(cv);
                                        }
                                    }
                                }
                            }
                            field.setValues(newValues);
                        }
                    }
                }
            }

            if (queryBean.isBudgetView()) {
                queryBean.updateSolrQueryString(contentManager, true);
            } else {
                queryBean.updateSolrQueryString(new SolrPrintPageService());
            }
            JsonObject json = new JsonObject();
            json.addProperty(SOLR_QUERY, queryBean.getSolrQueryString() != null ? queryBean.getSolrQueryString() : "");
            json.addProperty(REVERSE_QUERY, queryBean.getReverseQueryString() != null ? queryBean.getReverseQueryString() : "");
            return GSON.toJson(json);
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal("Error in running search", e);
        }
    }

    @GetMapping("eventid")
    public String timestamp(HttpServletRequest request) {
        DamUserContext.from(request).assertLoggedIn();
        JsonObject json = new JsonObject();
        try {
            json.addProperty("eventid", getSolrService().getLatestEventId());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Cannot get latest eventId from Solr, using 0", e);
            json.addProperty("eventid", 0);
        }
        return GSON.toJson(json);
    }

    @GetMapping(value = "getPolicyWrapper", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getPolicyWrapper(HttpServletRequest request,
                                   @RequestParam("onecmsid") String onecmsid) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        try {
            ContentVersionId latestVersion = contentManager.resolve(
                IdUtil.fromString(onecmsid), SYSTEM_SUBJECT);
            ContentResult<Object> cr = contentManager.get(
                latestVersion, null, Object.class, null, SYSTEM_SUBJECT);

            Object classType = cr.getContent().getContentAspect().getData();
            Object wrapperBean = null;

            if (classType instanceof DamImageAspectBean) {
                wrapperBean = new DamImageWrapperPolicyBean();
                ((DamImageWrapperPolicyBean) wrapperBean).setContentId(onecmsid);
            }
            if (classType instanceof DamWireImageAspectBean) {
                wrapperBean = new DamWireImageWrapperPolicyBean();
                ((DamWireImageWrapperPolicyBean) wrapperBean).setContentId(onecmsid);
            }
            if (classType instanceof OneImageBean) {
                wrapperBean = new OneImageWrapperPolicyBean();
                ((OneImageWrapperPolicyBean) wrapperBean).setContentId(onecmsid);
            }

            ContentId securityParentContentId = contentManager.resolve(
                GONG_SECURITY_PARENT, Subject.NOBODY_CALLER).getContentId();

            SetAliasOperation aliasOperation = new SetAliasOperation("externalId", onecmsid);
            InsertionInfoAspectBean insertionInfoAspectBean = new InsertionInfoAspectBean(securityParentContentId);

            ContentWriteBuilder<Object> cwb = new ContentWriteBuilder<>();
            cwb.mainAspectData(wrapperBean);
            cwb.operation(aliasOperation);
            cwb.aspect("p.InsertionInfo", insertionInfoAspectBean);

            ContentWrite<Object> content = cwb.buildCreate();

            ContentVersionId existingWrapperId = contentManager.resolve(onecmsid, Subject.NOBODY_CALLER);
            if (existingWrapperId == null) {
                ContentResult<Object> wrappedcr = contentManager.create(content, SYSTEM_SUBJECT);
                return wrappedcr.getContentId().getDelegationId() + ":" + wrappedcr.getContentId().getKey();
            } else {
                return existingWrapperId.getDelegationId() + ":" + existingWrapperId.getKey();
            }
        } catch (Exception e) {
            String msg = "Error getting the policy wrapper for onecmsid:" + onecmsid;
            LOGGER.log(Level.SEVERE, msg, e);
            throw ContentApiException.internal(msg, e);
        }
    }

    // ======== Password endpoints ========

    @PostMapping("changePassword")
    public void changePassword(HttpServletRequest request,
                               @RequestParam("pwd") String pwd) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        String userId = (String) request.getAttribute(com.atex.desk.api.auth.AuthFilter.USER_ATTRIBUTE);
        changeUserPassword(userId, pwd);
    }

    @PostMapping("changeUserPwd")
    public void changeUserPwd(HttpServletRequest request,
                              @RequestParam("userId") String userId,
                              @RequestParam("pwd") String pwd) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        changeUserPassword(userId, pwd);
    }

    private void changeUserPassword(String userId, String newPassword) {
        try {
            AppUser user = appUserRepository.findByLoginName(userId).orElse(null);
            if (user == null) {
                throw ContentApiException.notFound("User not found: " + userId);
            }
            user.setPasswordHash(passwordService.hashOldSha(newPassword));
            appUserRepository.save(user);
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating password", e);
            throw ContentApiException.internal("Error updating password: " + e.getMessage(), e);
        }
    }

    // ======== Permission endpoint ========

    @PostMapping(value = "permission", produces = MediaType.TEXT_PLAIN_VALUE)
    public String permission(HttpServletRequest request,
                             @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        AclBean obj = GSON.fromJson(body, AclBean.class);

        boolean checked = false;
        if (obj != null && !Strings.isNullOrEmpty(obj.getId())) {
            com.polopoly.cm.ContentId contentId = null;
            try {
                contentId = ContentIdFactory.createContentId(obj.getId());
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Cannot parse contentId: " + obj.getId());
            }

            String permission = obj.getPermission();
            if (obj.isDecorate() && permission != null && !permission.startsWith("21")) {
                permission = "21" + permission;
            }

            checked = ctx.havePermission(contentId, permission, true);
        } else {
            LOGGER.log(Level.FINE, "PARAMETERS NOT VALID");
        }

        return String.valueOf(checked);
    }

    // ======== Query update endpoint ========

    @PostMapping("queryUpdate")
    public String queryUpdate(HttpServletRequest request,
                              @RequestBody String body) {
        DamUserContext.from(request).assertLoggedIn();

        try {
            JsonObject jsonList = parseAsJsonObject(body);
            JsonArray qList = jsonList.get("qList").getAsJsonArray();
            JsonArray retList = new JsonArray();

            for (int i = 0; i < qList.size(); i++) {
                JsonObject query = qList.get(i).getAsJsonObject();
                String queryId = query.get("damid").getAsString();
                String solrQuery = query.get("solrQuery").getAsString();

                boolean allArchive = false;
                if (query.has("allArchive")) {
                    JsonElement elem = query.get("allArchive");
                    if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isBoolean()) {
                        allArchive = elem.getAsBoolean();
                    } else {
                        allArchive = Boolean.parseBoolean(elem.getAsString());
                    }
                }

                solrQuery = solrQuery.replaceAll("%20", " ");
                org.apache.solr.client.solrj.SolrQuery _solrQuery = new org.apache.solr.client.solrj.SolrQuery(solrQuery);

                int count;
                if (allArchive) {
                    count = getSolrService().recordCount(_solrQuery);
                } else {
                    count = getSolrServiceLatest().recordCount(_solrQuery);
                }

                JsonObject retQuery = new JsonObject();
                retQuery.addProperty("damid", queryId);
                retQuery.addProperty("count", count);
                retList.add(retQuery);
            }

            JsonObject resp = new JsonObject();
            if (retList.size() > 0) {
                resp.add("qList", retList);
            }
            return GSON.toJson(resp);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal("Error in running search", e);
        }
    }

    @GetMapping("autocomplete")
    public String autocomplete(HttpServletRequest request,
                               @RequestParam("str") String str,
                               @RequestParam("field") String field) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            return getSolrService().autocomplete(str, field);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal(e.getMessage(), e);
        }
    }

    @GetMapping("terms")
    public ResponseEntity<String> terms(HttpServletRequest request,
                                         @RequestParam("str") String str,
                                         @RequestParam(value = "limit", defaultValue = "-1") int limit,
                                         @RequestParam("field") String field) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            @SuppressWarnings("unchecked")
            String json = (String) OBJECT_CACHE.get("terms-" + str + "-" + field, () -> getSolrService().terms(str, field, limit));
            return ResponseEntity.ok()
                .cacheControl(CACHE_SHORT_PRIVATE)
                .body(json);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal(e.getMessage(), e);
        }
    }

    @GetMapping("currentuser")
    public ResponseEntity<Void> currentuser(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("/principals/users/me"))
            .build();
    }

    @GetMapping("getpage")
    public ResponseEntity<String> getpage(HttpServletRequest request,
                                           @RequestParam("pageid") String pageid) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            String queryString = "hermesPk_ss:" + "\"" + pageid + "\"";
            return ResponseEntity.ok(GSON.toJson(getSolrService().query(queryString, null, 0, null)));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot get references for " + pageid + ": " + e.getMessage(), e);
            throw ContentApiException.internal("cannot get references for " + pageid, e);
        }
    }

    // ======== User/Group endpoints ========

    @GetMapping("users")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> users(HttpServletRequest request,
                                         @RequestParam(value = "addGroupsToUsers", defaultValue = "false") boolean addGroupsToUsers) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            JsonObject resp = (JsonObject) OBJECT_CACHE.get(
                addGroupsToUsers ? "users-expanded" : "users",
                () -> getUserListAsJSON(addGroupsToUsers));
            return ResponseEntity.ok()
                .cacheControl(CACHE_SHORT_PRIVATE)
                .body(GSON.toJson(resp));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot retrieve users info " + e.getMessage(), e);
            throw ContentApiException.internal(e.getMessage(), e);
        }
    }

    private JsonObject getUserListAsJSON(boolean addGroupsToUsers) {
        JsonArray usersArray = new JsonArray();
        List<AppUser> allUsers = appUserRepository.findAll();

        // Build group membership lookup when needed
        Map<String, List<String>> userGroupNames = new HashMap<>();
        UserServer userServer = cmClient.getUserServer();
        GroupId[] allGroups = null;
        try {
            allGroups = userServer.getAllGroups();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Cannot retrieve groups: " + e.getMessage(), e);
        }

        if (allGroups != null) {
            for (GroupId gid : allGroups) {
                try {
                    Group group = userServer.findGroup(gid);
                    if (group != null) {
                        for (AppUser user : allUsers) {
                            if (group.isMember(user.getLoginName())) {
                                userGroupNames.computeIfAbsent(user.getLoginName(), k -> new ArrayList<>())
                                    .add(group.getName());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Cannot find group " + gid + ": " + e.getMessage(), e);
                }
            }
        }

        for (AppUser user : allUsers) {
            if (!user.isActive()) continue;
            JsonObject userJson = new JsonObject();
            userJson.addProperty("type", "user");
            userJson.addProperty("id", user.getLoginName());
            userJson.addProperty("name", user.getLoginName());
            userJson.addProperty("loginName", user.getLoginName());
            userJson.addProperty("principalId", user.getLoginName());
            if (addGroupsToUsers) {
                JsonArray groupList = new JsonArray();
                List<String> groups = userGroupNames.get(user.getLoginName());
                if (groups != null) {
                    for (String gn : groups) {
                        groupList.add(gn);
                    }
                }
                userJson.add("groupList", groupList);
            }
            usersArray.add(userJson);
        }

        // Append groups as entries in the Users array (original Polopoly behavior)
        if (allGroups != null) {
            for (GroupId gid : allGroups) {
                try {
                    Group group = userServer.findGroup(gid);
                    if (group != null) {
                        JsonObject groupJson = new JsonObject();
                        groupJson.addProperty("type", "group");
                        groupJson.addProperty("id", gid.getPrincipalIdString());
                        groupJson.addProperty("name", group.getName());
                        groupJson.addProperty("principalId", gid.getPrincipalIdString());
                        usersArray.add(groupJson);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Cannot find group " + gid + ": " + e.getMessage(), e);
                }
            }
        }

        JsonObject resp = new JsonObject();
        resp.add("Users", usersArray);
        return resp;
    }

    @GetMapping("user/info/{userId}")
    public ResponseEntity<String> getUserInfo(HttpServletRequest request,
                                               @PathVariable("userId") String userId) {
        DamUserContext.from(request).assertLoggedIn();

        try {
            ContentVersionId versionedUserId = contentManager.resolve(userId, SYSTEM_SUBJECT);
            ContentResult<UserDataBean> info = contentManager.get(
                versionedUserId, UserDataBean.class, SYSTEM_SUBJECT);

            JsonObject json = new JsonObject();
            if (info.getStatus().isSuccess() && info.getContent() != null) {
                UserDataBean user = info.getContent().getContentData();
                json.addProperty("firstname", user.getFirstname());
                json.addProperty("lastname", user.getSurname());
                json.addProperty("loginName", user.getLoginName());
            }

            return ResponseEntity.ok()
                .cacheControl(CACHE_SHORT_PRIVATE)
                .body(GSON.toJson(json));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot retrieve users info " + e.getMessage(), e);
            throw ContentApiException.internal(e.getMessage(), e);
        }
    }

    @GetMapping("groups")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> groups(HttpServletRequest request) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            JsonObject resp = (JsonObject) OBJECT_CACHE.get("groups_json", () -> {
                JsonObject r = new JsonObject();
                JsonArray groupsArray = new JsonArray();
                UserServer userServer = cmClient.getUserServer();
                GroupId[] allGroups = userServer.getAllGroups();
                if (allGroups != null) {
                    for (GroupId gid : allGroups) {
                        Group group = userServer.findGroup(gid);
                        if (group != null) {
                            JsonObject groupJson = new JsonObject();
                            groupJson.addProperty("type", "group");
                            groupJson.addProperty("id", gid.getPrincipalIdString());
                            groupJson.addProperty("name", group.getName());
                            groupJson.addProperty("principalId", gid.getPrincipalIdString());
                            groupsArray.add(groupJson);
                        }
                    }
                }
                r.add("Groups", groupsArray);
                return r;
            });
            return ResponseEntity.ok()
                .cacheControl(CACHE_SHORT_PRIVATE)
                .body(GSON.toJson(resp));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot retrieve groups info " + e.getMessage(), e);
            throw ContentApiException.internal(e.getMessage(), e);
        }
    }

    // ======== Collection update ========

    @PutMapping("update")
    public void updateEntryField(HttpServletRequest request,
                                 @RequestBody String body) throws ContentModifiedException {
        DamUserContext ctx = DamUserContext.from(request);
        Subject subject = ctx.assertLoggedIn().getSubject();

        CollectionAspect bean = GSON.fromJson(body, CollectionAspect.class);
        if (bean == null || bean.getContentId() == null) {
            throw ContentApiException.badRequest("Invalid update request");
        }

        ContentVersionId latestVersion = contentManager.resolve(IdUtil.fromString(bean.getContentId()), subject);
        ContentResult<Object> cr = contentManager.get(latestVersion, null, Object.class, null, subject);

        final CollectionAspect aspect;
        Object obj = cr.getContent().getAspectData(CollectionAspect.ASPECT_NAME);
        if (obj != null) {
            aspect = CollectionAspectUtils.update((CollectionAspect) obj, bean);
            if (aspect == null) {
                return;
            }
        } else {
            aspect = bean;
        }

        ContentWrite<Object> update = new ContentWriteBuilder<Object>()
            .origin(cr.getContent().getId())
            .type(cr.getContent().getContentDataType())
            .aspects(cr.getContent().getAspects())
            .mainAspect(cr.getContent().getContentAspect())
            .aspect(CollectionAspect.ASPECT_NAME, aspect)
            .buildUpdate();

        ContentId contentId = cr.getContent().getId().getContentId();
        ContentResult<Object> updateResult = contentManager.update(contentId, update, subject);
        Status status = updateResult.getStatus();
        if (status.isError()) {
            throw ContentApiException.error(
                "Cannot update " + IdUtil.toIdString(contentId),
                status.getDetailCode(), status.getHttpCode());
        }
    }

    // ======== Engagement endpoints ========

    @GetMapping(value = "clearEngage", produces = MediaType.TEXT_PLAIN_VALUE)
    public String clearEngage(HttpServletRequest request,
                              @RequestParam("contentId") String contentIdStr) {
        try {
            DamUserContext ctx = DamUserContext.from(request);
            Subject subject = ctx.assertLoggedIn().getSubject();

            ContentId id = IdUtil.fromString(contentIdStr);
            ContentVersionId latestVersion = contentManager.resolve(id, subject);
            ContentResult<Object> cr = contentManager.get(latestVersion, null, Object.class, null, subject);

            if (cr.getContent() != null &&
                cr.getContent().getAspect(EngagementAspect.ASPECT_NAME) != null) {

                ContentWriteBuilder<Object> cwb = new ContentWriteBuilder<Object>()
                    .origin(cr.getContent().getId())
                    .type(cr.getContent().getContentDataType())
                    .aspects(cr.getContent().getAspects())
                    .mainAspect(cr.getContent().getContentAspect());

                EngagementAspect ea = (EngagementAspect) cr.getContent()
                    .getAspect(EngagementAspect.ASPECT_NAME).getData();
                ea.getEngagementList().clear();
                cwb.aspect(EngagementAspectName, ea);

                ContentWrite<Object> content = cwb.buildUpdate();
                cr = contentManager.update(cr.getContent().getId().getContentId(), content, subject);
                Status status = cr.getStatus();
                if (status.isError()) {
                    throw ContentApiException.error(
                        String.format("Error while clearing engage for content %s", contentIdStr),
                        status.getDetailCode(), status.getHttpCode());
                }
            }
            return null;
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot clear engage " + contentIdStr + ": " + e.getMessage(), e);
            throw ContentApiException.internal(String.format(
                "Error while clearing engage for content %s: %s", contentIdStr, e.getMessage()));
        }
    }

    // ======== Export / Publish / Import ========

    @GetMapping(value = "export", produces = MediaType.TEXT_PLAIN_VALUE)
    public String export(HttpServletRequest request,
                         @RequestParam("contentId") String contentId,
                         @RequestParam(value = "appType", required = false) String appType,
                         @RequestParam(value = "domain", required = false) String domain,
                         @RequestParam(value = "inputTemplate", required = false) String inputTemplate,
                         @RequestParam(value = "destination", required = false) String destination,
                         @RequestParam(value = "status", required = false) String status) {
        try {
            DamUserContext ctx = DamUserContext.from(request);
            ctx.assertLoggedIn();
            BeanMapper beanMapper = new BeanMapper(contentManager);
            return beanMapper.export(IdUtil.fromString(contentId), ctx.getCaller(),
                appType, domain, inputTemplate, destination, status);
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "export failed for " + contentId + ": " + e.getMessage(), e);
            throw ContentApiException.internal("Export failed: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "publish", produces = MediaType.TEXT_PLAIN_VALUE)
    public String publish(HttpServletRequest request,
                          @RequestParam("contentId") String contentIdString) {
        try {
            DamUserContext ctx = DamUserContext.from(request);
            ctx.assertLoggedIn();
            assertParameter(contentIdString, "contentId");

            ContentId contentId = IdUtil.fromString(contentIdString);
            DamPublisher damPublisher = damPublisherFactory.create(contentId, ctx.getCaller());
            ContentId resultContentId = damPublisher.publish(contentId);
            return resultContentId != null ? IdUtil.toIdString(resultContentId) : null;
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() != null) {
                String causeMsg = e.getCause().getMessage();
                if ("CONTENT_LOCKED".equals(causeMsg)) {
                    throw ContentApiException.error("Locked Content", 50023, 500);
                } else if ("AUTHENTICATION_FAILED".equals(causeMsg)) {
                    throw ContentApiException.error("Remote Authentication Failed", 50001, 500);
                } else if (causeMsg != null && causeMsg.contains("ERROR_RESPONSE_RECEIVED")) {
                    throw ContentApiException.error(causeMsg, 50002, 500);
                }
            }
            LOGGER.log(Level.SEVERE, "cannot publish " + contentIdString + ": " + e.getMessage(), e);
            throw ContentApiException.internal(e.getMessage(), e);
        }
    }

    @PostMapping(value = "unpublish", produces = MediaType.TEXT_PLAIN_VALUE)
    public String unpublish(HttpServletRequest request,
                            @RequestParam("contentId") String contentIdString) {
        try {
            DamUserContext ctx = DamUserContext.from(request);
            ctx.assertLoggedIn();
            assertParameter(contentIdString, "contentId");

            ContentId contentId = IdUtil.fromString(contentIdString);
            DamPublisher damPublisher = damPublisherFactory.create(contentId, ctx.getCaller());
            ContentId resultContentId = damPublisher.unpublish(contentId);
            return resultContentId != null ? IdUtil.toIdString(resultContentId) : null;
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot unpublish " + contentIdString + ": " + e.getMessage(), e);
            throw ContentApiException.internal("Error in running export: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "import", produces = MediaType.TEXT_PLAIN_VALUE)
    public String importContent(HttpServletRequest request,
                                @RequestParam("backend") String backendId,
                                @RequestParam("ref") String ref) {
        try {
            DamUserContext ctx = DamUserContext.from(request);
            ctx.assertLoggedIn();
            assertParameter(ref, "ref");

            DamPublisher damPublisher = damPublisherFactory.create(backendId, ctx.getCaller());
            ContentId contentId = damPublisher.importContent(ref);
            return contentId != null ? IdUtil.toIdString(contentId) : null;
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() != null && "AUTHENTICATION_FAILED".equals(e.getCause().getMessage())) {
                throw ContentApiException.error("Remote Authentication Failed", 50001, 500);
            }
            LOGGER.log(Level.SEVERE, "cannot import " + ref + ": " + e.getMessage(), e);
            throw ContentApiException.internal("Error importing: " + e.getMessage(), e);
        }
    }

    @GetMapping(value = "resolve/contentid/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getPublishedVersion(@PathVariable("id") String id,
                                      @RequestParam(value = "view", defaultValue = ContentManager.SYSTEM_VIEW_LATEST) String view) {
        ContentVersionId contentVersionId = contentManager.resolve(IdUtil.fromString(id), view, Subject.NOBODY_CALLER);
        if (contentVersionId == null) {
            throw ContentApiException.notFound(String.format("Id (%s) not found on view: %s", id, view));
        }
        return IdUtil.toVersionedIdString(contentVersionId);
    }

    // ======== Preview / Gallery ========

    @Hidden
    @PostMapping(value = "collectionpreview", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getCollectionPreview(HttpServletRequest request,
                                       @RequestBody String body) {
        DamUserContext.from(request).assertLoggedIn();
        // Stub — requires ACE Web Preview integration
        throw ContentApiException.error("Collection preview not yet implemented", NOT_IMPLEMENTED, 501);
    }

    @GetMapping(value = "galleryurl", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getGalleryUrl(HttpServletRequest request,
                                @RequestParam("contentId") String contentId) {
        DamUserContext.from(request).assertLoggedIn();
        // Stub
        return null;
    }

    @GetMapping("getpreviewurl")
    public String getpreviewurl(HttpServletRequest request,
                                @RequestParam("contentId") String contentId,
                                @RequestParam("type") String type) {
        DamUserContext.from(request).assertLoggedIn();
        // Stub: return a desk preview URL
        if (!"collection".equals(type)) {
            return "/desk/?previewid=" + contentId;
        } else {
            return "/desk/#/collection/policy:" + contentId;
        }
    }

    // ======== Create content (with engagement) ========

    @PostMapping("createcontent")
    public String createcontent(HttpServletRequest request,
                                @RequestBody String body,
                                @RequestParam(value = "domain", required = false) String domain,
                                @RequestParam(value = "inputTemplate", required = false) String inputTemplate,
                                @RequestParam(value = "destination", required = false) String destination,
                                @RequestParam(value = "status", required = false) String status) {
        try {
            DamUserContext ctx = DamUserContext.from(request);
            ctx.assertLoggedIn();

            JsonObject json = parseAsJsonObject(body);
            String contentId = json.has("contentId") ? json.get("contentId").getAsString() : null;
            String appType = json.has("appType") ? json.get("appType").getAsString() : null;
            if (domain == null && json.has("domain")) {
                domain = json.get("domain").getAsString();
            }
            if (inputTemplate == null && json.has("inputTemplate")) {
                inputTemplate = json.get("inputTemplate").getAsString();
            }
            if (destination == null && json.has("destination")) {
                destination = json.get("destination").getAsString();
            }
            if (status == null && json.has("status")) {
                status = json.get("status").getAsString();
            }
            assertParameter(contentId, "contentId");

            BeanMapper beanMapper = new BeanMapper(contentManager);
            return beanMapper.export(IdUtil.fromString(contentId), ctx.getCaller(),
                appType, domain, inputTemplate, destination, status);
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "createcontent failed: " + e.getMessage(), e);
            throw ContentApiException.internal("Create content failed: " + e.getMessage(), e);
        }
    }

    // ======== Engagement management ========

    @PutMapping("engagement")
    public void engagement(HttpServletRequest request,
                           @RequestBody String body) {
        try {
            DamUserContext ctx = DamUserContext.from(request);
            ctx.assertLoggedIn();
            JsonObject jsonEng = parseAsJsonObject(body);
            String contentId = jsonEng.get("contentId").getAsString();
            EngagementDesc newEngagement = fromJSON(jsonEng, ctx.getCaller());

            DamEngagementUtils damEngagementUtils = new DamEngagementUtils(contentManager);
            damEngagementUtils.updateEngagement(IdUtil.fromString(contentId), newEngagement, ctx.getSubject());
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot update engagement", e);
            throw ContentApiException.internal("Error updating engagement: " + e.getMessage(), e);
        }
    }

    @PostMapping("addengagement")
    public void addengagement(HttpServletRequest request,
                              @RequestBody String body) {
        try {
            DamUserContext ctx = DamUserContext.from(request);
            ctx.assertLoggedIn();
            JsonObject jsonEng = parseAsJsonObject(body);
            String contentId = jsonEng.get("contentId").getAsString();
            EngagementDesc newEngagement = fromJSON(jsonEng, ctx.getCaller());

            DamEngagementUtils damEngagementUtils = new DamEngagementUtils(contentManager);
            damEngagementUtils.addEngagement(IdUtil.fromString(contentId), newEngagement, ctx.getSubject());
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot add engagement", e);
            throw ContentApiException.internal("Error adding engagement: " + e.getMessage(), e);
        }
    }

    @PostMapping("addpublevent")
    public void addPublEvent(HttpServletRequest request,
                             @RequestBody String body) {
        try {
            DamUserContext ctx = DamUserContext.from(request);
            Subject callerSubject = ctx.assertLoggedIn().getSubject();

            JsonObject jsonEng = parseAsJsonObject(body);
            String id = jsonEng.get("contentId").getAsString();

            ContentVersionId latestVersion = contentManager.resolve(IdUtil.fromString(id), callerSubject);
            ContentResult<Object> cr = contentManager.get(latestVersion, null, Object.class, null, callerSubject);

            JsonObject jsonPub = jsonEng.get("publevents").getAsJsonObject();
            if (jsonPub != null) {
                DamPubleventBean bean = DamWebServiceUtil.createEventFromJson(jsonPub);
                DamPubleventsAspectBean damPubleventsAspectBean;
                if (cr.getContent().getAspect(DamPubleventsAspectBean.ASPECT_NAME) != null) {
                    damPubleventsAspectBean = (DamPubleventsAspectBean) cr.getContent()
                        .getAspect(DamPubleventsAspectBean.ASPECT_NAME).getData();
                } else {
                    damPubleventsAspectBean = new DamPubleventsAspectBean();
                }

                damPubleventsAspectBean.getPublevents().add(bean);

                ContentWriteBuilder<Object> cwb = new ContentWriteBuilder<>();
                cwb.origin(cr.getContent().getId());
                cwb.type(cr.getContent().getContentDataType());
                cwb.aspects(cr.getContent().getAspects());
                cwb.mainAspect(cr.getContent().getContentAspect());
                cwb.aspect(DamPubleventsAspectBean.ASPECT_NAME, damPubleventsAspectBean);

                ContentWrite<Object> cw = cwb.buildUpdate();
                contentManager.update(latestVersion.getContentId(), cw, SYSTEM_SUBJECT);
            }
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot addpublevent: " + e.getMessage(), e);
            throw ContentApiException.internal("Error in running addpubevent: " + e.getMessage(), e);
        }
    }

    // ======== Solr reference endpoints ========

    @GetMapping("references")
    public ResponseEntity<String> references(HttpServletRequest request,
                                              @RequestParam("contentId") String contentId) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            return ResponseEntity.ok(GSON.toJson(getSolrService().resources(contentId, null)));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot get references for " + contentId, e);
            throw ContentApiException.internal("cannot get references for " + contentId, e);
        }
    }

    @GetMapping("related")
    public ResponseEntity<String> related(HttpServletRequest request,
                                           @RequestParam("id") String contentId) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            return ResponseEntity.ok(GSON.toJson(getSolrService().related(contentId, null)));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot get related for " + contentId, e);
            throw ContentApiException.internal("cannot get related for " + contentId, e);
        }
    }

    @GetMapping("jobs")
    public ResponseEntity<String> jobs(HttpServletRequest request,
                                        @RequestParam("contentId") String contentId) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            return ResponseEntity.ok(GSON.toJson(getSolrService().jobs(contentId, null)));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot get jobs for " + contentId, e);
            throw ContentApiException.internal("cannot get jobs for " + contentId, e);
        }
    }

    // ======== Camel route endpoints ========

    @PostMapping("updatecamelrules")
    public void updateCamelRules(HttpServletRequest request,
                                 @RequestBody String body) {
        DamUserContext.from(request).assertLoggedIn();
        DamRoutesListBean bean = GSON.fromJson(body, DamRoutesListBean.class);
        String authToken = DamUserContext.from(request).getAuthToken();
        CamelApiWebClient apiClient = new CamelApiWebClient(getDeskConfig(), authToken);
        apiClient.updateCamelRules(bean);
    }

    @PostMapping("addCamelRoute")
    public ResponseEntity<String> addCamelRoute(HttpServletRequest request,
                                                 @RequestBody String body) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            JsonObject jsonData = parseAsJsonObject(body);
            String fromUri = jsonData.get("fromUri").getAsString();
            String toUri = jsonData.get("toUri").getAsString();
            String authToken = DamUserContext.from(request).getAuthToken();
            CamelApiWebClient apiClient = new CamelApiWebClient(getDeskConfig(), authToken);
            apiClient.addCamelRoute(fromUri, toUri);
            return ResponseEntity.ok("{}");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot addCamelRoute: " + e.getMessage(), e);
            throw ContentApiException.internal("cannot addCamelRoute: " + e.getMessage(), e);
        }
    }

    @PostMapping("stopCamelRoute")
    public ResponseEntity<String> stopCamelRoute(HttpServletRequest request,
                                                  @RequestBody String body) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            JsonObject jsonData = parseAsJsonObject(body);
            String routeId = jsonData.get("routeId").getAsString();
            String authToken = DamUserContext.from(request).getAuthToken();
            CamelApiWebClient apiClient = new CamelApiWebClient(getDeskConfig(), authToken);
            apiClient.stopCamelRoute(routeId);
            return ResponseEntity.ok("{}");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot stopCamelRoute: " + e.getMessage(), e);
            throw ContentApiException.internal("cannot stopCamelRoute: " + e.getMessage(), e);
        }
    }

    @GetMapping("camelRoutes")
    public String camelRoutes(HttpServletRequest request,
                              @RequestParam("userId") String userId) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            String authToken = DamUserContext.from(request).getAuthToken();
            CamelApiWebClient apiClient = new CamelApiWebClient(getDeskConfig(), authToken);
            return apiClient.getCamelRoutes(userId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "cannot get camelRoutes for " + userId + ": " + e.getMessage(), e);
            throw ContentApiException.internal("cannot get camelRoutes: " + e.getMessage(), e);
        }
    }

    // ======== More-Like-This ========

    @GetMapping("mlt")
    public String mlt(HttpServletRequest request,
                      @RequestParam("q") String q,
                      @RequestParam(value = "mlt.fl", defaultValue = "description_atex_desk_ts,caption_atex_desk_ts,people_atex_desk_tms") String fl,
                      @RequestParam(value = "mlt.minwl", defaultValue = "4") String minwl,
                      @RequestParam(value = "mlt.mindf", defaultValue = "1") String mindf,
                      @RequestParam(value = "mlt.mintf", defaultValue = "0") String mintf,
                      @RequestParam(value = "mlt.interestingTerms", defaultValue = "list") String interestingTerms,
                      @RequestParam(value = "mlt.boost", defaultValue = "true") String boost,
                      @RequestParam(value = "mlt.match.include", defaultValue = "false") String matchInclude) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            return getSolrService().mlt(q, interestingTerms, boost, matchInclude, fl, minwl, mindf, mintf);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal(e.getMessage(), e);
        }
    }

    // ======== PDF merge ========

    @Hidden
    @PostMapping(value = "mergeMultiplePdf", produces = MediaType.TEXT_PLAIN_VALUE)
    public String mergeMultiplePdf(@RequestBody String body) {
        // PDF merge stub
        throw ContentApiException.error("PDF merge not yet implemented", NOT_IMPLEMENTED, 501);
    }

    // ======== Slack notification ========

    @PostMapping("send.job.message.by.parameters")
    public ResponseEntity<Void> sendJobNotification(HttpServletRequest request,
                                                     @RequestBody String body) {
        DamUserContext.from(request).assertLoggedIn();
        String host = DamUtils.getSlackModuleUrl();

        if (host != null) {
            JsonObject json = parseAsJsonObject(body);
            String authToken = DamUserContext.from(request).getAuthToken();
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(host + "/module-slack/content/send.job.message.by.parameters");
                httpPost.setEntity(new StringEntity(GSON.toJson(json)));
                httpPost.setHeader("Content-type", "application/json");
                httpPost.setHeader("X-Auth-Token", authToken);
                CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
                LOGGER.log(Level.FINE, String.valueOf(httpResponse.getStatusLine()));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                throw ContentApiException.internal(e.getMessage(), e);
            }
        } else {
            LOGGER.log(Level.FINE, "NO HOST DEFINED FOR SLACK MODULE");
        }

        return ResponseEntity.ok().build();
    }

    // ======== Remote content endpoints ========

    @GetMapping("contentid/{id}")
    public String getRemoteContentWithContentId(HttpServletRequest request,
                                                @PathVariable("id") String id,
                                                @RequestParam("backend") String backendId) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        assertParameter(id, "id");

        DamPublisher damPublisher = damPublisherFactory.create(backendId, ctx.getCaller());
        ContentId contentId = IdUtil.fromString(id);
        String json = damPublisher.getContent(contentId);
        if (StringUtil.isEmpty(json)) {
            throw ContentApiException.notFound(contentId);
        }
        return json;
    }

    @GetMapping("remote/contentrefjson/contentid/{id}")
    public ResponseEntity<String> getRemoteContentRefJson(HttpServletRequest request,
                                                           @RequestParam("backend") String backendId,
                                                           @PathVariable("id") String id) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        assertParameter(id, "id");
        ContentId sourceId = IdUtil.fromString(id);

        DamPublisher damPublisher = getDamPublisher(ctx, backendId);
        String json = damPublisher.getContent(sourceId);
        return ResponseEntity.ok(json);
    }

    @GetMapping("remote/contentref/contentid/{id}")
    public ResponseEntity<String> getRemoteContentRef(HttpServletRequest request,
                                                       @RequestParam("backend") String backendId,
                                                       @PathVariable("id") String id) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        assertParameter(id, "id");
        ContentId sourceId = IdUtil.fromString(id);

        DamPublisher damPublisher = getDamPublisher(ctx, backendId);
        RemoteContentRefBean remoteContentRef = damPublisher.getContentReference(sourceId);

        long now = System.currentTimeMillis();
        ContentResult<RemoteContentRefBean> res = new ContentResultBuilder<RemoteContentRefBean>()
            .id(new ContentVersionId(remoteContentRef.getContentId(), "DUMMY"))
            .mainAspectData(remoteContentRef)
            .status(Status.OK)
            .meta(new ContentResult.Meta(now, now))
            .variant("nocache")
            .build();

        return ResponseEntity.ok(GSON.toJson(res));
    }

    @GetMapping("publicationUrl/contentid/{id}")
    public String getRemotePublicationUrl(HttpServletRequest request,
                                          @PathVariable("id") String id) {
        try {
            DamUserContext ctx = DamUserContext.from(request);
            ctx.assertLoggedIn();
            assertParameter(id, "id");
            ContentId sourceId = IdUtil.fromString(id);

            DamPublisher damPublisher = getDamPublisher(ctx, sourceId);
            DamEngagement engagement = new DamEngagement(contentManager);
            ContentId remoteId = engagement.getEngagementId(sourceId);
            if (remoteId == null) {
                throw ContentApiException.error(
                    "Cannot get engagement from " + sourceId, NOT_IMPLEMENTED, 501);
            }

            String publicationUrlJson = damPublisher.getRemotePublicationUrl(sourceId, remoteId);
            String url = PublicationUrlJsonParser.getUrl(publicationUrlJson);
            if (StringUtil.notEmpty(url)) {
                PublishingContext context = damPublisherFactory.createContext(sourceId, ctx.getCaller());
                String newUrl = new DomainOverrider(context.getRemoteConfiguration()).fixJsPublicationUrl(url);
                return PublicationUrlJsonParser.toUrl(newUrl);
            }
            return publicationUrlJson;
        } catch (ContentApiException e) {
            throw e;
        } catch (UnsupportedOperationException e) {
            throw ContentApiException.error(e.getMessage(), NOT_IMPLEMENTED, 501);
        } catch (Exception e) {
            throw ContentApiException.internal(e.getMessage(), e);
        }
    }

    // ======== Restrict/unrestrict endpoints ========

    @PostMapping("restrict/{id}")
    public ResponseEntity<String> restrictContent(HttpServletRequest request,
                                                   @PathVariable("id") String id) {
        return restrictUnRestrictContent(request, id, true);
    }

    @PostMapping("unrestrict/{id}")
    public ResponseEntity<String> unRestrictContent(HttpServletRequest request,
                                                     @PathVariable("id") String id) {
        return restrictUnRestrictContent(request, id, false);
    }

    @GetMapping("canrestrict/{id}")
    public ResponseEntity<String> canRestrictContent(HttpServletRequest request,
                                                      @PathVariable("id") String id) {
        return canRestrictUnRestrictContent(request, id, true);
    }

    @GetMapping("canunrestrict/{id}")
    public ResponseEntity<String> canUnRestrictContent(HttpServletRequest request,
                                                        @PathVariable("id") String id) {
        return canRestrictUnRestrictContent(request, id, false);
    }

    // ======== Private helpers ========

    private ResponseEntity<String> canRestrictUnRestrictContent(HttpServletRequest request,
                                                                 String id, boolean isRestrict) {
        DamUserContext ctx = DamUserContext.from(request);
        Subject subject = ctx.assertLoggedIn().getSubject();
        assertParameter(id, "id");
        ContentId sourceId = IdUtil.fromString(id);
        RestrictContentService service = new RestrictContentService(contentManager);
        boolean cr = isRestrict
            ? service.canRestrictContent(sourceId, subject)
            : service.canUnRestrictContent(sourceId, subject);
        return ResponseEntity.ok(String.valueOf(cr));
    }

    private ResponseEntity<String> restrictUnRestrictContent(HttpServletRequest request,
                                                               String id, boolean isRestrict) {
        DamUserContext ctx = DamUserContext.from(request);
        Subject subject = ctx.assertLoggedIn().getSubject();
        assertParameter(id, "id");
        ContentId sourceId = IdUtil.fromString(id);
        RestrictContentService service = new RestrictContentService(contentManager);
        try {
            ContentResult<Object> cr = isRestrict
                ? service.restrictContent(sourceId, subject)
                : service.unRestrictContent(sourceId, subject);
            if (cr.getStatus().isSuccess()) {
                return ResponseEntity.ok(GSON.toJson(cr));
            } else {
                throw ContentApiException.error("Cannot unrestrict " + id, cr.getStatus());
            }
        } catch (ContentModifiedException e) {
            throw ContentApiException.internal("Content " + id + " has been modified", e);
        }
    }

    private Map<String, List<String>> buildCollapsedStatusMap() {
        Map<String, List<String>> collapsedStatusMap = new HashMap<>();
        try {
            ContentVersionId idStatusList = contentManager.resolve(WFStatusListBean.ASPECT_NAME, SYSTEM_SUBJECT);
            ContentResult<WFStatusListBean> statusList = contentManager.get(
                idStatusList, WFStatusListBean.class, SYSTEM_SUBJECT);
            WFStatusListBean statusListBean = statusList.getContent().getContentData();
            List<WFStatusBean> statuses = statusListBean.getStatus();
            List<WFStatusBean> newStatuses = new ArrayList<>();
            for (WFStatusBean wfStatusBean : statuses) {
                String checkingStatusID = wfStatusBean.getStatusID();
                String checkingStatusName = wfStatusBean.getName();
                boolean found = false;
                for (WFStatusBean newStatus : newStatuses) {
                    if (newStatus.getName().equals(checkingStatusName)) {
                        String statusKey = newStatus.getStatusID();
                        found = true;
                        collapsedStatusMap.computeIfAbsent(statusKey, k -> new ArrayList<>()).add(checkingStatusID);
                        break;
                    }
                }
                if (!found) {
                    newStatuses.add(wfStatusBean);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
        return collapsedStatusMap;
    }

    private JsonObject parseAsJsonObject(String body) {
        try {
            JsonElement jsonElement = JsonParser.parseString(body);
            if (!jsonElement.isJsonObject()) {
                throw ContentApiException.badRequest("The given input is not a json object");
            }
            return jsonElement.getAsJsonObject();
        } catch (com.google.gson.JsonParseException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.badRequest("The given input is not a json object");
        }
    }

    private void assertParameter(String value, String paramName) {
        if (StringUtil.isEmpty(value)) {
            throw ContentApiException.badRequest(String.format(
                "Missing '%s' parameter or value is empty", paramName));
        }
    }

    private DamPublisher getDamPublisher(DamUserContext ctx, ContentId contentId) {
        return damPublisherFactory.create(contentId, ctx.getCaller());
    }

    private DamPublisher getDamPublisher(DamUserContext ctx, String backendId) {
        return damPublisherFactory.create(backendId, ctx.getCaller());
    }

    private EngagementDesc fromJSON(JsonObject json, Caller caller) {
        EngagementDesc engagement = new EngagementDesc();
        if (JsonUtil.isNotNull(json, "appPk")) {
            engagement.setAppPk(json.get("appPk").getAsString());
        }
        if (JsonUtil.isNotNull(json, "appType")) {
            engagement.setAppType(json.get("appType").getAsString());
        }
        engagement.setUserName(caller.getLoginName());
        engagement.setTimestamp(Long.toString(new Date().getTime()));

        if (JsonUtil.isNotNull(json, "attributes")) {
            JsonArray ar = json.get("attributes").getAsJsonArray();
            List<EngagementElement> enList = engagement.getAttributes();
            for (int i = 0; i < ar.size(); i++) {
                EngagementElement en = new EngagementElement();
                JsonObject obj = ar.get(i).getAsJsonObject();
                en.setName(obj.get("name").getAsString());
                en.setValue(obj.get("value").getAsString());
                enList.add(en);
            }
            engagement.setAttributes(enList);
        }

        return engagement;
    }

    private boolean isUserAssignmentField(QueryField field) {
        String key = field.getKey();
        return key.equalsIgnoreCase("tag_dimension.sendto.Users_ss") ||
               key.equalsIgnoreCase("modifiedBy_s") ||
               key.equalsIgnoreCase("createdBy_s");
    }

    private DeskConfig getDeskConfig() {
        return DeskConfigLoader.getDeskConfig();
    }

    private String getContentApiUrl() {
        if (contentApiUrl == null) {
            DeskConfig deskConfig = getDeskConfig();
            if (deskConfig != null && deskConfig.getApiUrl() != null) {
                contentApiUrl = deskConfig.getApiUrl();
            } else {
                contentApiUrl = "http://localhost:8080/onecms";
            }
        }
        return contentApiUrl;
    }
}
