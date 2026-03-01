package com.atex.onecms.app.dam.ws;

import com.atex.onecms.content.ConfigurationDataBean;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.polopoly.util.StringUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/dam/locales")
@Tag(name = "DAM Locales")
public class DamLocaleResource {

    private static final Logger LOGGER = Logger.getLogger(DamLocaleResource.class.getName());
    private static final Subject SYSTEM_SUBJECT = Subject.of("98");
    private static final Gson GSON = new GsonBuilder().create();
    private static final CacheControl CACHE_LONG = CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();

    // Cache external IDs that were not found for 10 minutes
    private static final ConcurrentHashMap<String, Long> EXT_NOT_FOUND = new ConcurrentHashMap<>();
    private static final long EXT_NOT_FOUND_TTL = TimeUnit.MINUTES.toMillis(10);

    private final ContentManager contentManager;

    public DamLocaleResource(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    @GetMapping(value = "{app}/locale/{fileName:locale-[a-z]{2}\\.json}", produces = "application/json; charset=utf-8")
    public ResponseEntity<String> getLocale(@PathVariable("app") String appName,
                                             @PathVariable("fileName") String fileName,
                                             @RequestParam(value = "deflc", defaultValue = "en") String defaultLocale) {
        final String lc = getLocaleSuffix(fileName);
        final Optional<JsonObject> deskDefaultLocaleOpt;
        if (StringUtil.notEmpty(defaultLocale) && !defaultLocale.equalsIgnoreCase(lc)) {
            deskDefaultLocaleOpt = getDeskContentLocale(contentManager, defaultLocale).map(this::parseJson);
        } else {
            deskDefaultLocaleOpt = Optional.empty();
        }

        final Optional<JsonObject> deskLocaleOpt = getDeskContentLocale(contentManager, lc).map(this::parseJson);
        final Optional<JsonObject> starterKitLocaleOpt = getStarterKitContentLocale(contentManager, lc).map(this::parseJson);
        final Optional<JsonObject> appLocaleOpt;
        if (!"desk".equals(appName)) {
            appLocaleOpt = getAppContentLocale(contentManager, appName, lc).map(this::parseJson);
        } else {
            appLocaleOpt = Optional.empty();
        }

        final List<JsonObject> jsonList = Stream.of(
                deskDefaultLocaleOpt,
                deskLocaleOpt,
                starterKitLocaleOpt,
                appLocaleOpt,
                getConfigLocale("atex.onecms.copyfit.locale", lc).map(this::parseJson)
                    .filter(this::notEmpty).map(j -> prefixed(j, "COPYFIT")),
                getConfigLocale("atex.onecms.adm.locale", lc).map(this::parseJson),
                getConfigLocale("atex.onecms.custom.locale", lc).map(this::parseJson),
                getConfigLocale("atex.onecms." + appName.trim().toLowerCase() + ".locale", lc).map(this::parseJson)
            )
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(this::notEmpty)
            .collect(Collectors.toList());

        JsonObject baseJson = createBaseJson();
        for (JsonObject extJson : jsonList) {
            baseJson = mergeJson(baseJson, extJson);
        }
        if (notEmpty(baseJson)) {
            return ResponseEntity.ok().cacheControl(CACHE_LONG).body(GSON.toJson(baseJson));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private JsonObject createBaseJson() {
        final JsonObject json = new JsonObject();
        json.add("COPYFIT", new JsonObject());
        json.addProperty("LANGUAGE_en", "English");
        json.addProperty("LANGUAGE_it", "Italiano");
        json.addProperty("LANGUAGE_de", "Deutsch");
        json.addProperty("LANGUAGE_nl", "Nederlands");
        json.addProperty("LANGUAGE_sv", "Svenska");
        json.addProperty("LANGUAGE_es", "Español");
        json.addProperty("LANGUAGE_dk", "Dansk");
        json.addProperty("LANGUAGE_no", "Norsk");
        json.addProperty("LANGUAGE_vi", "Tiếng Việt");
        json.addProperty("LANGUAGE_tr", "Türkçe");
        return json;
    }

    private JsonObject mergeJson(JsonObject baseJson, JsonObject extJson) {
        for (Map.Entry<String, JsonElement> entry : extJson.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            JsonElement existing = baseJson.get(key);
            if (existing != null && existing.isJsonObject()) {
                if (value != null && !value.isJsonNull() && value.isJsonObject()) {
                    baseJson.add(key, mergeJson(existing.getAsJsonObject(), value.getAsJsonObject()));
                }
            } else {
                if (value != null && !value.isJsonNull()) {
                    baseJson.add(key, value);
                } else {
                    baseJson.remove(key);
                }
            }
        }
        return baseJson;
    }

    private boolean notEmpty(JsonObject json) {
        return json != null && !json.isJsonNull() && !json.entrySet().isEmpty();
    }

    private JsonObject prefixed(JsonObject json, String prefix) {
        JsonObject obj = new JsonObject();
        obj.add(prefix, json);
        return obj;
    }

    private JsonObject parseJson(String json) {
        try {
            JsonElement el = JsonParser.parseString(json);
            if (el.isJsonObject()) return el.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
        return new JsonObject();
    }

    private String getLocaleSuffix(String fileName) {
        int idx = fileName.indexOf("-");
        String lc = fileName.substring(idx + 1);
        if (lc.endsWith(".json")) return lc.substring(0, lc.length() - 5);
        return lc;
    }

    private Optional<String> getDeskContentLocale(ContentManager cm, String lc) {
        return getAppContentLocale(cm, "desk", lc);
    }

    private Optional<String> getStarterKitContentLocale(ContentManager cm, String lc) {
        return getAppContentLocale(cm, "starterKit", lc);
    }

    @SuppressWarnings("unchecked")
    private Optional<String> getAppContentLocale(ContentManager cm, String appName, String lc) {
        String externalId = String.format("atex.onecms.%s.locale.%s", appName.toLowerCase(), lc.trim().toLowerCase());
        try {
            ContentVersionId vid = cm.resolve(externalId, SYSTEM_SUBJECT);
            if (vid != null) {
                ContentResult<Object> cr = cm.get(vid, null, Object.class, null, SYSTEM_SUBJECT);
                if (cr.getStatus().isSuccess() && cr.getContent() != null) {
                    Object data = cr.getContent().getContentData();
                    if (data instanceof Map<?,?> map) {
                        Object json = map.get("json");
                        if (json instanceof String s && StringUtil.notEmpty(s)) {
                            return Optional.of(s.trim());
                        }
                        // If the content data IS the locale JSON, serialize it
                        return Optional.of(GSON.toJson(data));
                    }
                    if (data instanceof ConfigurationDataBean bean) {
                        return Optional.ofNullable(bean.getJson()).map(String::trim).filter(StringUtil::notEmpty);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Cannot get locale " + externalId, e);
        }
        return Optional.empty();
    }

    private Optional<String> getConfigLocale(String externalId, String lc) {
        // Check not-found cache
        Long notFoundTime = EXT_NOT_FOUND.get(externalId);
        if (notFoundTime != null && (System.currentTimeMillis() - notFoundTime) < EXT_NOT_FOUND_TTL) {
            return Optional.empty();
        }

        String fullId = externalId + "." + lc;
        try {
            ContentVersionId vid = contentManager.resolve(fullId, SYSTEM_SUBJECT);
            if (vid != null) {
                ContentResult<Object> cr = contentManager.get(vid, null, Object.class, null, SYSTEM_SUBJECT);
                if (cr.getStatus().isSuccess() && cr.getContent() != null) {
                    Object data = cr.getContent().getContentData();
                    if (data instanceof Map<?,?>) {
                        return Optional.of(GSON.toJson(data));
                    }
                }
            } else {
                EXT_NOT_FOUND.put(externalId, System.currentTimeMillis());
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Cannot get config locale " + fullId, e);
        }
        return Optional.empty();
    }
}

