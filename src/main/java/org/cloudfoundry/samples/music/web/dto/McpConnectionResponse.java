package org.cloudfoundry.samples.music.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.cloudfoundry.samples.music.domain.McpServerConnection;

public record McpConnectionResponse(UUID id, String name, String baseUrl, String endpoint, boolean enabled,
        String status, Instant lastSuccessfulAt, Instant lastFailureAt, String lastErrorMessage,
        Integer toolCount, List<String> availableTools, String simplifiedTools) {

    public static McpConnectionResponse fromEntity(McpServerConnection entity) {
        List<String> tools = entity.getAvailableTools();
        String simplifiedTools = getSimplifiedToolNames(tools);

        return new McpConnectionResponse(
            entity.getId(),
            entity.getName(),
            entity.getBaseUrl(),
            entity.getEndpoint(),
            entity.isEnabled(),
            entity.getStatus().name(),
            entity.getLastSuccessfulAt(),
            entity.getLastFailureAt(),
            entity.getLastErrorMessage(),
            entity.getToolCount(),
            tools,
            simplifiedTools
        );
    }

    private static String getSimplifiedToolNames(List<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }

        List<String> simplified = tools.stream()
            .map(tool -> tool.replaceAll("^[a-z_]+_m_c_p__[^_]*__", "")
                            .replaceAll("^mcp__[^_]*__", "")
                            .replace("_", " ")
                            .trim())
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toList());

        if (simplified.isEmpty()) {
            return "";
        }

        if (simplified.size() <= 3) {
            return String.join(", ", simplified);
        }

        return String.join(", ", simplified.subList(0, 3)) + " (+" + (simplified.size() - 3) + " more)";
    }
}

