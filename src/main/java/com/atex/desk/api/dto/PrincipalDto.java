package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User principal info")
public class PrincipalDto
{
    @Schema(description = "The user ID")
    private String userId;

    @Schema(description = "The username")
    private String username;

    @Schema(description = "Account creation time (epoch milliseconds)")
    private String createdAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
