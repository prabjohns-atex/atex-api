package com.atex.desk.api.controller;

import com.atex.desk.api.auth.AuthFilter;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.dto.PrincipalDto;
import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.repository.AppUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/principals")
@Tag(name = "Principals")
public class PrincipalsController
{
    private final AppUserRepository userRepository;

    public PrincipalsController(AppUserRepository userRepository)
    {
        this.userRepository = userRepository;
    }

    @GetMapping("/users/me")
    @Operation(summary = "Get current authenticated user")
    @ApiResponse(responseCode = "200", description = "Current user info",
                 content = @Content(schema = @Schema(implementation = PrincipalDto.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request)
    {
        String userId = resolveUserId(request);
        if (userId == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "No authenticated user"));
        }

        try
        {
            int id = Integer.parseInt(userId);
            return userRepository.findById(id)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toDto(user)))
                .orElseGet(() -> notFound("User not found: " + userId));
        }
        catch (NumberFormatException e)
        {
            // userId might be a username string
            return userRepository.findByUsername(userId)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toDto(user)))
                .orElseGet(() -> notFound("User not found: " + userId));
        }
    }

    @GetMapping("/users")
    @Operation(summary = "List all users")
    public List<PrincipalDto> getUsers()
    {
        return userRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user by ID")
    @ApiResponse(responseCode = "200", description = "User found",
                 content = @Content(schema = @Schema(implementation = PrincipalDto.class)))
    @ApiResponse(responseCode = "404", description = "User not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getUser(
        @Parameter(description = "User ID") @PathVariable Integer userId)
    {
        return userRepository.findById(userId)
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toDto(user)))
            .orElseGet(() -> notFound("User not found: " + userId));
    }

    @GetMapping("/groups")
    @Operation(summary = "List all groups")
    public List<?> getGroups()
    {
        return List.of();
    }

    private PrincipalDto toDto(AppUser user)
    {
        PrincipalDto dto = new PrincipalDto();
        dto.setUserId(user.getUserId().toString());
        dto.setUsername(user.getUsername());
        if (user.getCreatedAt() != null)
        {
            dto.setCreatedAt(String.valueOf(user.getCreatedAt().toEpochMilli()));
        }
        return dto;
    }

    private String resolveUserId(HttpServletRequest request)
    {
        Object user = request.getAttribute(AuthFilter.USER_ATTRIBUTE);
        return user != null ? user.toString() : null;
    }

    private ResponseEntity<ErrorResponseDto> notFound(String message)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponseDto("NOT_FOUND", message));
    }
}
