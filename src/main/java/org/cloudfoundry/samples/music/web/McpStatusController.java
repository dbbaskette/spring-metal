package org.cloudfoundry.samples.music.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cloudfoundry.samples.music.service.McpServerConnectionService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("mcp")  // Only requires MCP profile, not LLM
public class McpStatusController {

    private final McpServerConnectionService connectionService;

    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;

    @Autowired
    public McpStatusController(ObjectProvider<McpServerConnectionService> connectionServiceProvider) {
        this.connectionService = connectionServiceProvider.getIfAvailable();
    }

    @RequestMapping(value = "/api/mcp/status", method = RequestMethod.GET)
    public Map<String, Object> getMcpStatus() {
        List<Map<String, Object>> servers = new ArrayList<>();
        if (connectionService != null) {
            connectionService.listConnections().forEach(connection -> {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", connection.getId() != null ? connection.getId().toString() : null);
                summary.put("name", connection.getName());
                summary.put("baseUrl", connection.getBaseUrl());
                summary.put("endpoint", connection.getEndpoint());
                summary.put("enabled", connection.isEnabled());
                summary.put("status", connection.getStatus() != null ? connection.getStatus().name() : "UNKNOWN");
                summary.put("lastSuccessfulAt", connection.getLastSuccessfulAt());
                summary.put("lastFailureAt", connection.getLastFailureAt());
                summary.put("lastErrorMessage", connection.getLastErrorMessage());
                if (connection.getDefaultHeaders() != null && !connection.getDefaultHeaders().isEmpty()) {
                    summary.put("headers", connection.getDefaultHeaders());
                }
                servers.add(summary);
            });
        }

        boolean hasActiveServer = servers.stream()
                .anyMatch(server -> Boolean.TRUE.equals(server.get("enabled"))
                        && "ACTIVE".equals(server.get("status")));

        boolean toolsExposed = toolCallbackProvider != null && hasActiveServer;

        // Calculate actual tool count from active servers
        int totalToolCount = 0;
        if (toolsExposed && toolCallbackProvider != null) {
            try {
                totalToolCount = toolCallbackProvider.getToolCallbacks().length;
            } catch (Exception e) {
                // Fallback to sum from server entities if ToolCallbackProvider fails
                totalToolCount = servers.stream()
                    .filter(server -> Boolean.TRUE.equals(server.get("enabled"))
                                   && "ACTIVE".equals(server.get("status")))
                    .mapToInt(server -> {
                        Object toolCountObj = server.get("toolCount");
                        return toolCountObj instanceof Integer ? (Integer) toolCountObj : 0;
                    })
                    .sum();
            }
        }

        return Map.of(
                "enabled", toolsExposed,
                "message", toolsExposed ? "MCP tools are available via dynamic registration"
                        : "No active MCP servers registered",
                "toolCount", totalToolCount,
                "servers", servers
        );
    }
}