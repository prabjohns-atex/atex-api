package com.atex.desk.api.controller;

import com.atex.onecms.app.dam.ws.ContentApiException;
import com.atex.onecms.content.Subject;
import com.atex.onecms.ws.activity.Activity;
import com.atex.onecms.ws.activity.ActivityException;
import com.atex.onecms.ws.activity.ActivityInfo;
import com.atex.onecms.ws.activity.ActivityServiceSecured;
import com.atex.onecms.ws.activity.ApplicationInfo;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/activities")
@Tag(name = "Activities")
public class ActivityController {

    private static final Logger LOGGER = Logger.getLogger(ActivityController.class.getName());
    private static final Gson GSON = new Gson();

    private final ActivityServiceSecured activityService;

    public ActivityController(ActivityServiceSecured activityService) {
        this.activityService = activityService;
    }

    @GetMapping("{activityId}")
    @Operation(summary = "Get activity", description = "Read activity info for a content ID")
    public ResponseEntity<String> get(@PathVariable("activityId") String activityId,
                                       HttpServletRequest request) {
        Subject subject = getSubject(request);
        try {
            ActivityInfo info = activityService.get(subject, activityId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GSON.toJson(info));
        } catch (ActivityException e) {
            LOGGER.log(Level.WARNING, "Failed to get activity", e);
            throw ContentApiException.error(e.getMessage(), e.getStatus());
        }
    }

    @PutMapping(value = "{activityId}/{userId}/{applicationId}",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add/update activity", description = "Register an activity for a user/application pair")
    public ResponseEntity<String> put(@PathVariable("activityId") String activityId,
                                       @PathVariable("userId") String userId,
                                       @PathVariable("applicationId") String applicationId,
                                       @RequestBody String entity,
                                       HttpServletRequest request) {
        Activity activity;
        try {
            activity = GSON.fromJson(entity, Activity.class);
        } catch (JsonSyntaxException e) {
            throw ContentApiException.badRequest("Could not parse request entity.");
        }
        if (activity == null || activity.getActivity() == null
                || activity.getActivity().isEmpty()) {
            throw ContentApiException.badRequest("Missing activity.");
        }

        Subject subject = getSubject(request);
        try {
            ApplicationInfo appInfo = new ApplicationInfo(
                    System.currentTimeMillis(),
                    activity.getActivity(),
                    activity.getParams());
            ActivityInfo result = activityService.write(subject, activityId, userId,
                    applicationId, appInfo);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GSON.toJson(result));
        } catch (ActivityException e) {
            LOGGER.log(Level.WARNING, "Failed to update activity", e);
            throw ContentApiException.error(e.getMessage(), e.getStatus());
        }
    }

    @DeleteMapping("{activityId}/{userId}/{applicationId}")
    @Operation(summary = "Remove activity", description = "Unregister an activity for a user/application pair")
    public ResponseEntity<String> delete(@PathVariable("activityId") String activityId,
                                          @PathVariable("userId") String userId,
                                          @PathVariable("applicationId") String applicationId,
                                          HttpServletRequest request) {
        Subject subject = getSubject(request);
        try {
            ActivityInfo result = activityService.delete(subject, activityId, userId,
                    applicationId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GSON.toJson(result));
        } catch (ActivityException e) {
            LOGGER.log(Level.WARNING, "Failed to delete activity", e);
            throw ContentApiException.error(e.getMessage(), e.getStatus());
        }
    }

    private Subject getSubject(HttpServletRequest request) {
        Object userId = request.getAttribute("desk.auth.user");
        if (userId != null) {
            return new Subject(userId.toString(), null);
        }
        return Subject.NOBODY_CALLER;
    }
}
