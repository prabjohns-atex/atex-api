package com.atex.desk.api.controller;

import com.atex.desk.api.auth.AuthFilter;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.dto.PrincipalDto;
import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.repository.AppUserRepository;
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
public class PrincipalsController
{
    private final AppUserRepository userRepository;

    public PrincipalsController(AppUserRepository userRepository)
    {
        this.userRepository = userRepository;
    }

    @GetMapping("/users/me")
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
    public List<PrincipalDto> getUsers()
    {
        return userRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUser(@PathVariable Integer userId)
    {
        return userRepository.findById(userId)
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toDto(user)))
            .orElseGet(() -> notFound("User not found: " + userId));
    }

    @GetMapping("/groups")
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
