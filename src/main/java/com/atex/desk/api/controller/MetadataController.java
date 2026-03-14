package com.atex.desk.api.controller;

import com.atex.desk.api.service.MetadataService;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Metadata;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Metadata/taxonomy REST endpoints.
 * Provides taxonomy structure browsing, autocomplete, annotation, and entity resolution
 * used by the pTags widget in mytype-new.
 */
@RestController
@RequestMapping("/metadata")
@Tag(name = "Metadata")
public class MetadataController {

    private static final Logger LOG = Logger.getLogger(MetadataController.class.getName());
    private static final Gson GSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
        .create();

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    /**
     * Get taxonomy/dimension structure by ID.
     * Returns dimension with entity tree up to specified depth.
     */
    @GetMapping(value = "/structure/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getStructure(
            @PathVariable("id") String metadataObjectId,
            @RequestParam(value = "depth", defaultValue = "100") int depth) {
        try {
            Object result = metadataService.getStructure(metadataObjectId, depth);
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(GSON.toJson(result));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error reading metadata structure for " + metadataObjectId, e);
            return ResponseEntity.internalServerError()
                .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Autocomplete entities within a dimension.
     * Uses Solr faceted search to find matching entities.
     */
    @GetMapping(value = "/complete/{dimension}/{prefix:.*}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> complete(
            @PathVariable("dimension") String dimensionId,
            @PathVariable("prefix") String entityPrefix,
            @RequestParam(value = "lang", defaultValue = "__default__") String language,
            @RequestParam(value = "format", required = false) String format) {
        if (entityPrefix == null || entityPrefix.length() < 2) {
            return ResponseEntity.badRequest()
                .body("{\"error\":\"Two or more characters required for completion\"}");
        }

        try {
            Dimension result = metadataService.complete(dimensionId, entityPrefix, language);
            return ResponseEntity.ok(GSON.toJson(result));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error completing metadata for " + dimensionId + "/" + entityPrefix, e);
            return ResponseEntity.internalServerError()
                .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Annotate text to suggest matching taxonomy entities.
     * mytype-new sends JSON: { taxonomyId, annotationString }
     */
    @PostMapping(value = "/annotate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> annotate(@RequestBody String body) {
        try {
            JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            String taxonomyId = json.has("taxonomyId") ? json.get("taxonomyId").getAsString() : null;
            String text = json.has("annotationString") ? json.get("annotationString").getAsString() : null;

            if (taxonomyId == null || text == null) {
                return ResponseEntity.badRequest()
                    .body("{\"error\":\"Missing taxonomyId or annotationString\"}");
            }

            Metadata result = metadataService.annotate(taxonomyId, text);
            return ResponseEntity.ok(GSON.toJson(result));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error annotating text", e);
            return ResponseEntity.internalServerError()
                .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Resolve content-backed entities.
     * mytype-new sends JSON: { data: { dimensions: [...] } }
     */
    @PostMapping(value = "/lookup", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> resolveEntities(@RequestBody String body) {
        try {
            JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : json;

            Metadata metadata = GSON.fromJson(data, Metadata.class);
            if (metadata == null || metadata.getDimensions() == null) {
                return ResponseEntity.badRequest()
                    .body("{\"error\":\"No metadata payload provided\"}");
            }

            Metadata resolved = metadataService.resolveContentBackedEntities(metadata);
            return ResponseEntity.ok(GSON.toJson(resolved));
        } catch (JsonSyntaxException e) {
            return ResponseEntity.badRequest()
                .body("{\"error\":\"Invalid metadata payload\"}");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error resolving metadata entities", e);
            return ResponseEntity.internalServerError()
                .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
