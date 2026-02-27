package com.atex.onecms.app.dam.util;

import com.atex.onecms.app.dam.publevent.DamPubleventBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class DamWebServiceUtil {

    private static final Gson GSON = new GsonBuilder().create();

    public static DamPubleventBean createEventFromJson(JsonObject json) {
        if (json == null) return new DamPubleventBean();
        return GSON.fromJson(json, DamPubleventBean.class);
    }

    public static JsonElement parseJsonInputStream(InputStream stream,
                                                    Supplier<com.atex.common.collections.Pair<String, com.atex.onecms.content.Status>> errorSupplier) {
        if (stream == null) return null;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (Exception e) {
            return null;
        }
    }
}
