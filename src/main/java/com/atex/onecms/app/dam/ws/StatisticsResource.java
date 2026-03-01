package com.atex.onecms.app.dam.ws;

import com.atex.onecms.app.dam.config.DeskSystemConfiguration;
import com.atex.onecms.app.dam.publish.DamPublisher;
import com.atex.onecms.app.dam.publish.DamPublisherFactory;
import com.atex.onecms.app.dam.publish.DomainOverrider;
import com.atex.onecms.app.dam.publish.PublicationUrlJsonParser;
import com.atex.onecms.app.dam.publish.PublishingContext;
import com.atex.onecms.app.dam.standard.aspects.PublicationLinkSupport;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.polopoly.util.StringUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/dam/content/statistics")
@Tag(name = "DAM Statistics")
public class StatisticsResource {

    private static final Logger LOGGER = Logger.getLogger(StatisticsResource.class.getName());
    private static final Gson GSON = new GsonBuilder().create();

    private final ContentManager contentManager;
    private final DamPublisherFactory damPublisherFactory;

    public StatisticsResource(ContentManager contentManager, DamPublisherFactory damPublisherFactory) {
        this.contentManager = contentManager;
        this.damPublisherFactory = damPublisherFactory;
    }

    @GetMapping(value = "embed/site/{domain}", produces = "application/json; charset=utf-8")
    public ResponseEntity<String> getSiteStatisticsEmbed(HttpServletRequest request,
                                                          @PathVariable("domain") String domain) {
        DamUserContext ctx = DamUserContext.from(request);
        Subject subject = ctx.assertLoggedIn().getSubject();

        if (StringUtil.isEmpty(domain)) throw ContentApiException.badRequest("domain is required");

        final DeskSystemConfiguration systemConfig = DeskSystemConfiguration.fetch(contentManager, subject);
        final Map<String, String> domains = systemConfig.getPlausibleDomains();
        if (domains.isEmpty()) {
            throw ContentApiException.badRequest(
                "No domains are configured for plausible, check atex.configuration.desk-system-configuration");
        }
        final String shareUrl = domains.get(domain.trim().toLowerCase());
        if (StringUtil.isEmpty(shareUrl)) {
            throw ContentApiException.badRequest(
                "The domain " + domain + " has not been configured in atex.configuration.desk-system-configuration");
        }
        return buildPlausibleResponse(systemConfig, domain, shareUrl);
    }

    @GetMapping(value = "embed/contentid/{id}", produces = "application/json; charset=utf-8")
    public ResponseEntity<String> getContentStatisticsEmbed(HttpServletRequest request,
                                                             @PathVariable("id") String id) {
        DamUserContext ctx = DamUserContext.from(request);
        Subject subject = ctx.assertLoggedIn().getSubject();

        if (StringUtil.isEmpty(id)) throw ContentApiException.badRequest("id is required");

        final ContentId contentId = IdUtil.fromString(id);
        final ContentVersionId versionId = contentManager.resolve(contentId, subject);
        if (versionId == null) throw ContentApiException.notFound(contentId);

        final ContentResult<Object> cr = contentManager.get(versionId, null, Object.class, null, subject);
        if (!cr.getStatus().isSuccess()) {
            throw ContentApiException.error("Cannot get " + IdUtil.toVersionedIdString(versionId), cr.getStatus());
        }

        final Object contentData = cr.getContent().getContentData();
        if (!(contentData instanceof PublicationLinkSupport)) {
            throw ContentApiException.badRequest("Content type " + cr.getContent().getContentDataType()
                + " does not support the publicationLink property");
        }

        final DeskSystemConfiguration systemConfig = DeskSystemConfiguration.fetch(contentManager, subject);
        final Map<String, String> domains = systemConfig.getPlausibleDomains();
        if (domains.isEmpty()) {
            throw ContentApiException.badRequest(
                "No domains are configured for plausible, check atex.configuration.desk-system-configuration");
        }

        final DamPublisher damPublisher = damPublisherFactory.create(contentId, ctx.getCaller());
        final String publicationLinkJson = damPublisher.getRemotePublicationUrl(contentId, null);
        if (StringUtil.isEmpty(publicationLinkJson)) {
            throw ContentApiException.notFound("Content " + IdUtil.toIdString(contentId) + " has not been published yet");
        }

        final String publicationLink = PublicationUrlJsonParser.getUrl(publicationLinkJson);
        if (StringUtil.isEmpty(publicationLink)) {
            throw ContentApiException.notFound("Content " + IdUtil.toIdString(contentId) + " has not been published yet");
        }

        String newPublicationLink = fixJsPublicationUrl(ctx, contentId, publicationLink);
        final Matcher matcher = Pattern.compile("(https?://)([^/]+)(.*)").matcher(newPublicationLink);
        if (!matcher.matches()) {
            throw ContentApiException.badRequest("The content url " + newPublicationLink + " is not supported");
        }

        final String domain = matcher.group(2).toLowerCase();
        final String baseShareUrl = domains.get(domain);
        if (StringUtil.isEmpty(baseShareUrl)) {
            throw ContentApiException.badRequest(
                "The domain " + domain + " has not been configured in atex.configuration.desk-system-configuration");
        }
        final String shareUrl = baseShareUrl + "&page="
            + URLEncoder.encode(matcher.group(3), StandardCharsets.UTF_8);

        return buildPlausibleResponse(systemConfig, domain, shareUrl);
    }

    private String fixJsPublicationUrl(DamUserContext ctx, ContentId contentId, String url) {
        if (StringUtil.notEmpty(url)) {
            final PublishingContext context = damPublisherFactory.createContext(contentId, ctx.getCaller());
            return new DomainOverrider(context.getRemoteConfiguration()).fixJsPublicationUrl(url);
        }
        return url;
    }

    private ResponseEntity<String> buildPlausibleResponse(DeskSystemConfiguration systemConfig,
                                                           String domain, String shareUrl) {
        final JsonObject json = new JsonObject();
        json.addProperty("shareUrl", shareUrl);
        final String plausibleEmbed = systemConfig.getPlausibleEmbeds()
            .getOrDefault(domain, systemConfig.getPlausibleDefaultEmbed());
        if (StringUtil.notEmpty(plausibleEmbed) && systemConfig.usePlausibleEmbed()) {
            json.addProperty("embed", plausibleEmbed.replace("%URL%", shareUrl));
        }
        return ResponseEntity.ok(GSON.toJson(json));
    }
}

