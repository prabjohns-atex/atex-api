package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Matches the OneCMS ErrorResponse JSON structure.
 * Reference format: {"extraInfo":{},"statusCode":40400,"message":"NOT_FOUND"}
 */
@Schema(description = "Error response with status code and message")
public class ErrorResponseDto
{
    @Schema(description = "Extra information about the error", example = "{}")
    private Map<String, Object> extraInfo = Map.of();

    @Schema(description = "The internal status code (HTTP status × 100)", example = "40400")
    private int statusCode;

    @Schema(description = "The error message")
    private String message;

    public ErrorResponseDto() {}

    public ErrorResponseDto(int statusCode, String message)
    {
        this.statusCode = statusCode;
        this.message = message;
    }

    public ErrorResponseDto(HttpStatus status, String message)
    {
        this.statusCode = status.value() * 100;
        this.message = message;
    }

    public Map<String, Object> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, Object> extraInfo) { this.extraInfo = extraInfo; }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
