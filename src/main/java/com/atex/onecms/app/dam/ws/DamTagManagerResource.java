package com.atex.onecms.app.dam.ws;

import com.atex.common.collections.Pair;
import com.atex.onecms.app.dam.tagmanager.TagManagerService;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.polopoly.metadata.Entity;
import com.polopoly.util.StringUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

@RestController
@RequestMapping("/dam/tagmanager")
@Tag(name = "DAM Tag Manager")
public class DamTagManagerResource {

    private static final Logger LOGGER = Logger.getLogger(DamTagManagerResource.class.getName());
    private static final Gson GSON = new GsonBuilder().create();
    private static final AtomicReference<TagManagerService> tagManagerService = new AtomicReference<>();

    private final ContentManager contentManager;

    public DamTagManagerResource(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    @GetMapping("lookup/{parentId}/{dimensionId}")
    public ResponseEntity<String> lookupTags(HttpServletRequest request,
                                              @PathVariable("parentId") String parentId,
                                              @PathVariable("dimensionId") String dimensionId,
                                              @RequestParam("name") String name,
                                              @RequestParam(value = "start", required = false) Integer start) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        if (StringUtil.isEmpty(name)) throw ContentApiException.badRequest("name is required");

        try {
            int qStart = Math.max(0, Optional.ofNullable(start).orElse(0));
            List<Entity> entities = getTagManagerService().lookupTag(
                dimensionId, name, parentId, qStart, ctx.getSubject());
            return ResponseEntity.ok(GSON.toJson(entities));
        } catch (Exception e) {
            throw ContentApiException.internal("Error looking up tags", e);
        }
    }

    @GetMapping("search/{parentId}/{dimensionId}")
    public ResponseEntity<String> searchTag(HttpServletRequest request,
                                             @PathVariable("parentId") String parentId,
                                             @PathVariable("dimensionId") String dimensionId,
                                             @RequestParam("name") String name) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        if (StringUtil.isEmpty(name)) throw ContentApiException.badRequest("name is required");

        try {
            Optional<Entity> entity = getTagManagerService().searchTag(
                dimensionId, name, parentId, ctx.getSubject());
            if (entity.isPresent()) {
                return ResponseEntity.ok(GSON.toJson(entity.get()));
            } else {
                throw ContentApiException.notFound("Cannot find tag " + name + " in " + dimensionId);
            }
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            throw ContentApiException.internal("Error searching tag " + name + " in " + dimensionId, e);
        }
    }

    @PostMapping("create/{parentId}/{dimensionId}")
    public ResponseEntity<String> createTag(HttpServletRequest request,
                                             @PathVariable("parentId") String parentId,
                                             @PathVariable("dimensionId") String dimensionId,
                                             @RequestParam("name") String name,
                                             @RequestParam(value = "securityParentId", required = false) String securityParentId) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        if (StringUtil.isEmpty(name)) throw ContentApiException.badRequest("name is required");

        try {
            Subject subject = ctx.getSubject();
            String secParent = (StringUtil.notEmpty(securityParentId)) ? securityParentId : "dam.assets.common.d";

            Pair<ContentId, com.atex.onecms.content.Status> cr = getTagManagerService().createTag(
                dimensionId, name, parentId, secParent, subject);
            if (!cr.right().isSuccess()) {
                throw ContentApiException.error("Cannot create tag " + name + " in " + dimensionId, cr.right());
            }
            ContentId contentId = cr.left();
            Pair<Entity, com.atex.onecms.content.Status> result = getTagManagerService().tagToEntity(contentId, subject);
            if (result.right().isSuccess()) {
                return ResponseEntity.ok(GSON.toJson(result.left()));
            } else {
                throw ContentApiException.error("Cannot get the created tag " + name + " id "
                    + IdUtil.toIdString(contentId), result.right());
            }
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            throw ContentApiException.internal("Error creating tag " + name + " in " + dimensionId, e);
        }
    }

    private TagManagerService getTagManagerService() {
        if (tagManagerService.get() == null) {
            tagManagerService.set(new TagManagerService(contentManager));
        }
        return tagManagerService.get();
    }
}

