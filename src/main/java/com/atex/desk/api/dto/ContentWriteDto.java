package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Matches the OneCMS Content JSON structure for create/update requests.
 */
@Schema(description = "The content model used for create and update operations")
public class ContentWriteDto
{
    @Schema(description = "The non versioned contentId")
    private String id;

    @Schema(description = "The versioned contentId")
    private String version;

    @Schema(description = "Aspects, 'contentData' contains the main aspect")
    private Map<String, AspectDto> aspects;

    @Schema(description = "Operations to apply (e.g. SetAliasOperation)")
    private List<OperationDto> operations;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Map<String, AspectDto> getAspects() { return aspects; }
    public void setAspects(Map<String, AspectDto> aspects) { this.aspects = aspects; }

    public List<OperationDto> getOperations() { return operations; }
    public void setOperations(List<OperationDto> operations) { this.operations = operations; }

    /**
     * Generic operation DTO. The 'type' field selects the operation kind.
     * Currently supported: "SetAliasOperation" with namespace + value fields.
     */
    public static class OperationDto
    {
        @Schema(description = "Operation type, e.g. 'SetAliasOperation'")
        private String type;

        @Schema(description = "Alias namespace (for SetAliasOperation)", example = "externalId")
        private String namespace;

        @Schema(description = "Alias value (for SetAliasOperation)", example = "p.onecms.DamTemplateList")
        private String value;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
