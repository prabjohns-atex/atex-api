package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Login credentials")
public class CredentialsDto
{
    @Schema(description = "The username", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "The password", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }
}
