package com.atex.desk.api.dto;

/**
 * Matches the OneCMS ErrorResponse JSON structure.
 */
public class ErrorResponseDto
{
    private String detailCode;
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
