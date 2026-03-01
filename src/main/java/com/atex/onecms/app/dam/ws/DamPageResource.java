package com.atex.onecms.app.dam.ws;

import com.atex.gong.data.publish.page.Article;
import com.atex.gong.data.publish.page.Container;
import com.atex.gong.data.publish.page.DuplicatePage;
import com.atex.gong.data.publish.page.GsonFactory;
import com.atex.gong.data.publish.page.Page;
import com.atex.gong.data.publish.page.PageResponse;
import com.atex.onecms.app.dam.publish.ContentPublisher;
import com.atex.onecms.app.dam.publish.DamPublisher;
import com.atex.onecms.app.dam.publish.DamPublisherFactory;
import com.atex.onecms.app.dam.publish.ModulePublisher;
import com.atex.onecms.app.dam.publish.PublishingContext;
import com.atex.onecms.app.dam.util.HttpDamUtils;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.polopoly.util.StringUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/dam/page")
@Tag(name = "DAM Page")
public class DamPageResource {

    private static final Logger LOGGER = Logger.getLogger(DamPageResource.class.getName());
    private static final Gson GSON = GsonFactory.create();
    private static final CacheControl NO_CACHE = CacheControl.noCache().mustRevalidate();

    private final ContentManager contentManager;
    private final DamPublisherFactory damPublisherFactory;

    public DamPageResource(ContentManager contentManager, DamPublisherFactory damPublisherFactory) {
        this.contentManager = contentManager;
        this.damPublisherFactory = damPublisherFactory;
    }

    @PostMapping(value = "publish", produces = "application/json")
    public ResponseEntity<String> publishPage(HttpServletRequest request,
                                               @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        Subject subject = ctx.assertLoggedIn().getSubject();

        long start = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();
        try {
            JsonElement jsonElement = JsonParser.parseString(body);
            Page page = Page.from(jsonElement);

            LOGGER.info("publishing " + page.getName() + " (" + page.getExternalId() + ") [" + uuid + "]");

            ContentVersionId pageContentVersionId = contentManager.resolve(
                toPolopolyExternalId(page.getExternalId()), subject);

            DamPublisher damPublisher = getDamPublisher(page, pageContentVersionId, ctx);
            ModulePublisher modulePublisher = damPublisher.createModulePublisher();
            ContentPublisher contentPublisher = modulePublisher.createContentPublisher(damPublisher.getUserName());

            Map<ContentId, ContentId> publishedContents = new HashMap<>();
            for (Container c : page.getContainers()) {
                for (Article a : c.getArticles()) {
                    // Publish teaser map references
                    if (a.getTeaserMap() != null) {
                        for (Map.Entry<String, String> e : a.getTeaserMap().entrySet()) {
                            try {
                                if (StringUtil.isEmpty(e.getValue())) continue;
                                if (e.getValue().startsWith("onecms:")) {
                                    ContentId id = IdUtil.fromString(e.getValue());
                                    if (!publishedContents.containsKey(id)) {
                                        ContentId remoteId = publishContent(damPublisher, id);
                                        publishedContents.put(id, remoteId);
                                    }
                                    Optional.ofNullable(publishedContents.get(id))
                                        .map(IdUtil::toIdString)
                                        .ifPresent(remoteId -> a.getTeaserMap().put(e.getKey(), remoteId));
                                }
                            } catch (IllegalArgumentException ignore) { }
                        }
                    }
                    // Publish article content
                    if (!StringUtil.isEmpty(a.getContentId())) {
                        ContentId contentId = IdUtil.fromString(a.getContentId());
                        if ("draft".equals(contentId.getDelegationId())) {
                            a.setContentId(null);
                        } else if ("onecms".equals(contentId.getDelegationId())
                            && !publishedContents.containsKey(contentId)) {
                            ContentId remoteId = publishContent(damPublisher, contentId);
                            a.setContentId(IdUtil.toIdString(remoteId));
                            publishedContents.put(contentId, remoteId);
                        }
                    }
                }
            }

            // Transform contentIds to remote format
            transformToRemoteContentId(contentPublisher, page);

            // Build page JSON for the backend
            Page newPage = new Page(page);
            newPage.setAceId(null);
            String newPageJson = GSON.toJson(newPage.toJson());

            HttpDamUtils.WebServiceResponse response = contentPublisher.publishPage(newPageJson);
            if (response == null) {
                throw ContentApiException.internal("invalid response");
            }
            if (response.isFailed()) {
                throw ContentApiException.internal(response.getError());
            }

            PageResponse resultPage = GSON.fromJson(response.getBody(), PageResponse.class);
            return ResponseEntity.ok(GSON.toJson(resultPage.toJson()));
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            throw ContentApiException.internal(e.getMessage(), e);
        } finally {
            LOGGER.info("done [" + uuid + "] in " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    @GetMapping(value = "contentid/{id}/slots", produces = "application/json")
    public ResponseEntity<String> getPageSlot(HttpServletRequest request,
                                               @PathVariable("id") String id,
                                               @RequestParam(value = "backend", required = false) String backendId) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        DamPublisher damPublisher = damPublisherFactory.create(backendId, ctx.getCaller());
        ModulePublisher modulePublisher = damPublisher.createModulePublisher();
        ContentPublisher contentPublisher = modulePublisher.createContentPublisher(damPublisher.getUserName());

        try {
            String content = contentPublisher.getContent(IdUtil.fromString(id));
            if (content == null) {
                throw ContentApiException.internal("no response from remote api");
            }
            return ResponseEntity.ok().cacheControl(NO_CACHE).body(content);
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            throw ContentApiException.internal("Error getting page slots", e);
        }
    }

    @GetMapping(value = "externalid/{id}/slots", produces = "application/json")
    public ResponseEntity<String> getPageSlotFromExternalId(HttpServletRequest request,
                                                             @PathVariable("id") String id) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        ContentVersionId pageVid = contentManager.resolve(id, ctx.getSubject());
        if (pageVid == null) {
            throw ContentApiException.badRequest("Page " + id + " cannot be found in desk");
        }

        DamPublisher damPublisher = damPublisherFactory.create(pageVid.getContentId(), ctx.getCaller());
        ModulePublisher modulePublisher = damPublisher.createModulePublisher();
        ContentPublisher contentPublisher = modulePublisher.createContentPublisher(damPublisher.getUserName());

        try {
            String content = contentPublisher.getContent(pageVid.getContentId());
            if (content == null) {
                throw ContentApiException.internal("no response from remote api");
            }
            return ResponseEntity.ok().cacheControl(NO_CACHE).body(content);
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            throw ContentApiException.internal("Error getting page slots", e);
        }
    }

    @PostMapping(value = "duplicate", produces = "application/json")
    public ResponseEntity<String> duplicatePage(HttpServletRequest request,
                                                 @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        Subject subject = ctx.assertLoggedIn().getSubject();

        long start = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();

        try {
            DuplicatePage duplicatePage = GSON.fromJson(body, DuplicatePage.class);

            String sourcePageId = assertParameter(duplicatePage.getSourcePageId(), "sourcePageId is required");
            String targetParentId = assertParameter(duplicatePage.getTargetParentId(), "targetParentId is required");

            LOGGER.info("source page: " + sourcePageId + " target parent: " + targetParentId + " [" + uuid + "]");

            ContentVersionId sourcePageVid = contentManager.resolve(
                toPolopolyExternalId(duplicatePage.getSourcePageId()), subject);

            DamPublisher damPublisher;
            if (sourcePageVid == null) {
                if (StringUtil.isEmpty(duplicatePage.getAceId())) {
                    throw ContentApiException.badRequest("Page " + duplicatePage.getSourcePageId() + " cannot be found");
                }
                Optional<String> backendIdOpt = damPublisherFactory.getBackendIdFromApiDomain(
                    duplicatePage.getAceId(), ctx.getCaller());
                if (backendIdOpt.isPresent()) {
                    damPublisher = damPublisherFactory.create(backendIdOpt.get(), ctx.getCaller());
                } else {
                    throw ContentApiException.badRequest("No backend found for API domain " + duplicatePage.getAceId());
                }
            } else {
                damPublisher = damPublisherFactory.create(sourcePageVid.getContentId(), ctx.getCaller());
            }

            ModulePublisher modulePublisher = damPublisher.createModulePublisher();
            ContentPublisher contentPublisher = modulePublisher.createContentPublisher(damPublisher.getUserName());

            HttpDamUtils.WebServiceResponse response = contentPublisher.duplicatePage(
                sourcePageId, targetParentId, duplicatePage.getTitle(), duplicatePage.getDescription());

            if (response.isFailed()) {
                throw ContentApiException.internal(response.getError());
            }

            JsonObject json = new JsonObject();
            json.addProperty("pageId", response.getBody());
            return ResponseEntity.ok(GSON.toJson(json));
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            throw ContentApiException.internal(e.getMessage(), e);
        } finally {
            LOGGER.info("done [" + uuid + "] in " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    // --- Helper methods ---

    private DamPublisher getDamPublisher(Page page, ContentVersionId pageVid, DamUserContext ctx) {
        if (pageVid == null) {
            if (StringUtil.isEmpty(page.getAceId())) {
                throw ContentApiException.badRequest("Page " + page.getName()
                    + " (" + page.getExternalId() + ") cannot be found in desk");
            }
            Optional<String> backendIdOpt = damPublisherFactory.getBackendIdFromApiDomain(
                page.getAceId(), ctx.getCaller());
            if (backendIdOpt.isPresent()) {
                return damPublisherFactory.create(backendIdOpt.get(), ctx.getCaller());
            }
            throw ContentApiException.badRequest("No backend found for API domain " + page.getAceId());
        }
        return damPublisherFactory.create(pageVid.getContentId(), ctx.getCaller());
    }

    private ContentId publishContent(DamPublisher damPublisher, ContentId contentId) {
        try {
            ContentId remoteId = damPublisher.publish(contentId);
            if (remoteId == null) {
                throw ContentApiException.internal("Publishing " + IdUtil.toIdString(contentId) + " did not produce output");
            }
            return remoteId;
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            throw ContentApiException.internal("Cannot publish " + IdUtil.toIdString(contentId), e);
        }
    }

    private void transformToRemoteContentId(ContentPublisher contentPublisher, Page page) {
        for (Container c : page.getContainers()) {
            for (Article a : c.getArticles()) {
                a.setContentId(contentPublisher.toRemoteContentId(a.getContentId()));
                Map<String, String> map = a.getTeaserMap();
                if (map != null) {
                    for (Map.Entry<String, String> e : map.entrySet()) {
                        e.setValue(contentPublisher.toRemoteContentId(e.getValue()));
                    }
                }
            }
        }
    }

    private String toPolopolyExternalId(String pageId) {
        return Optional.ofNullable(pageId)
            .filter(StringUtil::notEmpty)
            .map(String::trim)
            .map(s -> s.replace("contentid/", ""))
            .orElse(null);
    }

    private String assertParameter(String value, String message) {
        if (StringUtil.isEmpty(value)) throw ContentApiException.badRequest(message);
        return value.trim();
    }
}

