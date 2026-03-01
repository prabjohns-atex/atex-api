package com.atex.onecms.app.dam.ws;

import com.atex.onecms.app.dam.publishingschedule.DamPublishingScheduleUtils;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.polopoly.util.StringUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
@RequestMapping("/dam/scheduling")
@Tag(name = "DAM Scheduling")
public class DamSchedulingResource {

    private static final Logger LOGGER = Logger.getLogger(DamSchedulingResource.class.getName());

    private final ContentManager contentManager;

    public DamSchedulingResource(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    @PostMapping(value = "implementScheduleInstance", produces = "application/json; charset=utf-8")
    public ResponseEntity<String> implementScheduleInstance(HttpServletRequest request,
                                                             @RequestBody String body) {
        DamUserContext ctx = DamUserContext.from(request);
        Subject subject = ctx.assertLoggedIn().getSubject();

        String id = null;
        try {
            JsonObject jw = JsonParser.parseString(body).getAsJsonObject();
            String contentIdString = jw.has("contentId") ? jw.get("contentId").getAsString() : "";
            long pubDate = jw.has("pubDate") ? jw.get("pubDate").getAsLong() : 0L;

            if (StringUtil.isEmpty(contentIdString)) {
                throw ContentApiException.badRequest("contentId is required");
            }

            ContentId contentId = IdUtil.fromString(contentIdString);

            DamPublishingScheduleUtils utils = new DamPublishingScheduleUtils(
                contentManager, null, subject, null);
            ContentId resultContentId = utils.implementPlan(contentId, pubDate);

            if (resultContentId != null) {
                id = IdUtil.toIdString(resultContentId);
            }
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            throw ContentApiException.internal("Error: " + e.getMessage(), e);
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("id", id);
        return ResponseEntity.ok(resp.toString());
    }
}

