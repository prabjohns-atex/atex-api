package com.atex.desk.api.controller;

import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.service.TypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Content type schema endpoint.
 * Returns ModelTypeBean responses generated from @AspectDefinition-annotated bean classes.
 */
@RestController
@RequestMapping("/content/type")
@Tag(name = "Content Types")
public class TypeController {

    private final TypeService typeService;

    public TypeController(TypeService typeService) {
        this.typeService = typeService;
    }

    /**
     * GET /content/type/{typeName}
     * Returns a ModelTypeBean describing the fields and Java types for a content type.
     * Used by mytype-new to determine StructuredText fields and coerce values.
     */
    @GetMapping("/{typeName}")
    @Operation(summary = "Get content type schema",
               description = "Returns a ModelTypeBean response with field introspection for the given aspect type name.")
    @ApiResponse(responseCode = "200", description = "Type schema found",
                 content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(responseCode = "404", description = "Type not found")
    public ResponseEntity<?> getType(@PathVariable String typeName) {
        Map<String, Object> type = typeService.getType(typeName);
        if (type == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDto(HttpStatus.NOT_FOUND, "Type not found: " + typeName));
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
            .body(type);
    }
}
