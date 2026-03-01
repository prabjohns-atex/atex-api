package com.atex.onecms.app.dam.ws;

import com.atex.onecms.app.dam.ui.MavenProject;
import com.atex.onecms.app.dam.ui.VersionInfo;
import com.atex.onecms.app.dam.ui.VersionInfo.UIModuleInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/dam/ui")
@Tag(name = "DAM UI")
public class DamUIResource {

    private static final Logger LOGGER = Logger.getLogger(DamUIResource.class.getName());
    private static final Gson GSON = new GsonBuilder().create();

    @GetMapping("system/versions")
    public ResponseEntity<String> getUIVersion() {
        try {
            final JsonObject json = new JsonObject();
            final List<UIModuleInfo> versions = VersionInfo.getVersions();
            final JsonArray uiVersions = new JsonArray();
            json.add("versions", uiVersions);
            versions.stream().map(GSON::toJsonTree).forEach(uiVersions::add);

            final List<MavenProject> modules = VersionInfo.getModules();
            final JsonArray dependencies = new JsonArray();
            json.add("modules", dependencies);
            modules.stream().map(GSON::toJsonTree).forEach(dependencies::add);

            return ResponseEntity.ok(GSON.toJson(json));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal("cannot get ui versions");
        }
    }
}

