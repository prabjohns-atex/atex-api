package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Matches the OneCMS ErrorResponse JSON structure.
 */
@Schema(description = "Error response with status code and message")
public class ErrorResponseDto
{
    @Schema(description = "The internal status code", example = "40400")
    private String detailCode;

    @Schema(description = "The error message")
    private String name;

    public ErrorResponseDto() {}

    public ErrorResponseDto(String detailCode, String name)
    {
        this.detailCode = detailCode;
        this.name = name;
    }

    public String getDetailCode() { return detailCode; }
    public void setDetailCode(String detailCode) { this.detailCode = detailCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
