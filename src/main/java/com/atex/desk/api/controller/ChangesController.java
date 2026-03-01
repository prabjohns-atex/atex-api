package com.atex.desk.api.controller;

import com.atex.desk.api.dto.ChangeFeedDto;
import com.atex.desk.api.service.ChangeListService;
import com.atex.desk.api.service.InvalidCommitIdException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/changes")
@Tag(name = "Changes")
public class ChangesController
{
    private final ChangeListService changeListService;

    public ChangesController(ChangeListService changeListService) {
        this.changeListService = changeListService;
    }

    @GetMapping
    @Operation(summary = "Get content change feed",
        description = "Returns a chronological stream of content lifecycle events (CREATE, UPDATE, DELETE). "
            + "Use commitId as a cursor for polling.")
    @ApiResponse(responseCode = "200", description = "Change feed returned successfully")
    @ApiResponse(responseCode = "400", description = "Invalid parameter")
    @ApiResponse(responseCode = "404", description = "commitId exceeds current maximum")
    public ResponseEntity<?> getChanges(
            @Parameter(description = "Return changes with commitId > value (cursor)")
            @RequestParam(required = false) Long commitId,

            @Parameter(description = "Return changes since time (epoch ms, ignored if commitId set)")
            @RequestParam(required = false) Long changedSince,

            @Parameter(description = "Content type filter (aspect names)")
            @RequestParam(required = false) List<String> content,

            @Parameter(description = "Object type filter (* = disable)")
            @RequestParam(required = false, defaultValue = "article,image,page,graphic,collection")
            List<String> object,

            @Parameter(description = "Partition filter")
            @RequestParam(required = false) List<String> partition,

            @Parameter(description = "Event type filter (* = disable)")
            @RequestParam(required = false, defaultValue = "CREATE,UPDATE,DELETE")
            List<String> event,

            @Parameter(description = "Max results")
            @RequestParam(required = false, defaultValue = "100") int rows) {

        try {
            // Handle * wildcard → disable filter
            List<String> objectTypes = isWildcard(object) ? null : object;
            List<String> eventTypes = isWildcard(event) ? null : event;

            if (rows < 1) rows = 1;
            if (rows > 10000) rows = 10000;

            ChangeFeedDto feed = changeListService.queryChanges(
                commitId, changedSince, content, objectTypes, partition, eventTypes, rows);

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(feed);

        } catch (InvalidCommitIdException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(java.util.Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    private static boolean isWildcard(List<String> values) {
        return values != null && values.size() == 1 && "*".equals(values.getFirst());
    }
}
