package com.atex.plugins.layout;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/layout/handj")
@Tag(name = "Layout")
@ConditionalOnBean(LayoutService.class)
public class LayoutResource {

    private final LayoutService layoutService;

    public LayoutResource(LayoutService layoutService) {
        this.layoutService = layoutService;
    }

    @GetMapping("preview-url/{id}")
    @Operation(summary = "Get print preview image", description = "Proxy to layout server for preview image retrieval")
    public ResponseEntity<?> previewURL(@PathVariable("id") String previewId,
                                         HttpServletRequest request) {
        return layoutService.previewURL(previewId, request);
    }

    @GetMapping("{type}/{id}")
    @Operation(summary = "Get layout data", description = "Proxy GET request to layout server by type and content ID")
    public ResponseEntity<?> getLayoutRequest(@PathVariable("type") String fieldType,
                                               @PathVariable("id") String contentId,
                                               HttpServletRequest request) {
        return layoutService.getLayoutRequest(fieldType, contentId, request);
    }

    @GetMapping("{type}/{subtype}/{id}")
    @Operation(summary = "Get layout data with subtype", description = "Proxy GET request to layout server by type, subtype, and content ID")
    public ResponseEntity<?> getLayoutRequestWithSubtype(@PathVariable("type") String fieldType,
                                                          @PathVariable("subtype") String fieldSubType,
                                                          @PathVariable("id") String contentId,
                                                          HttpServletRequest request) {
        return layoutService.getLayoutRequest(fieldType, fieldSubType, contentId, request);
    }

    @PostMapping("{type}/{id}")
    @Operation(summary = "Post layout data", description = "Proxy POST request to layout server by type and content ID")
    public ResponseEntity<?> postLayoutRequest(@RequestBody String body,
                                                @PathVariable("type") String fieldType,
                                                @PathVariable("id") String contentId,
                                                HttpServletRequest request) {
        return layoutService.postLayoutRequest(body, fieldType, contentId, request);
    }

    @PostMapping("{type}/{subtype}/{id}")
    @Operation(summary = "Post layout data with subtype", description = "Proxy POST request to layout server by type, subtype, and content ID")
    public ResponseEntity<?> postLayoutRequestWithSubtype(@RequestBody String body,
                                                           @PathVariable("type") String fieldType,
                                                           @PathVariable("subtype") String fieldSubType,
                                                           @PathVariable("id") String contentId,
                                                           HttpServletRequest request) {
        return layoutService.postLayoutRequest(body, fieldType, fieldSubType, contentId, request);
    }
}
