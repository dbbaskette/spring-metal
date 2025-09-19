package org.cloudfoundry.samples.music.config.mcp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.modelcontextprotocol.client.McpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cloudfoundry.samples.music.service.DynamicMcpClientManager;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "type", havingValue = "ASYNC")
@Profile("mcp")
public class DynamicMcpToolCallbackProvider implements ToolCallbackProvider {

    private static final Logger logger = LoggerFactory.getLogger(DynamicMcpToolCallbackProvider.class);

    private final DynamicMcpClientManager clientManager;
    private final ObjectProvider<List<McpAsyncClient>> baseClientsProvider;
    private final ObjectProvider<McpToolFilter> toolFilterProvider;
    private final ObjectProvider<McpToolNamePrefixGenerator> namePrefixProvider;
    private final ObjectProvider<ToolContextToMcpMetaConverter> metaConverterProvider;

    public DynamicMcpToolCallbackProvider(DynamicMcpClientManager clientManager,
            ObjectProvider<List<McpAsyncClient>> baseClientsProvider,
            ObjectProvider<McpToolFilter> toolFilterProvider,
            ObjectProvider<McpToolNamePrefixGenerator> namePrefixProvider,
            ObjectProvider<ToolContextToMcpMetaConverter> metaConverterProvider) {
        this.clientManager = clientManager;
        this.baseClientsProvider = baseClientsProvider;
        this.toolFilterProvider = toolFilterProvider;
        this.namePrefixProvider = namePrefixProvider;
        this.metaConverterProvider = metaConverterProvider;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        List<McpAsyncClient> clients = new ArrayList<>();

        List<McpAsyncClient> baseClients = baseClientsProvider.getIfAvailable(() -> List.<McpAsyncClient>of());
        if (baseClients != null) {
            clients.addAll(baseClients);
        }

        Collection<McpAsyncClient> dynamicClients = clientManager.getActiveAsyncClients();
        clients.addAll(dynamicClients);

        logger.info("🔧 MCP Tool Discovery: Found {} total MCP clients ({} base + {} dynamic)",
                   clients.size(), baseClients != null ? baseClients.size() : 0, dynamicClients.size());

        if (clients.isEmpty()) {
            logger.warn("⚠️  No MCP clients available - no tools will be registered");
            return new ToolCallback[0];
        }

        AsyncMcpToolCallbackProvider.Builder builder = AsyncMcpToolCallbackProvider.builder().mcpClients(clients);

        McpToolFilter filter = toolFilterProvider.getIfUnique(() -> (client, tool) -> true);
        if (filter != null) {
            builder.toolFilter(filter);
        }

        McpToolNamePrefixGenerator prefixGenerator = namePrefixProvider
                .getIfUnique(McpToolNamePrefixGenerator::defaultGenerator);
        if (prefixGenerator != null) {
            builder.toolNamePrefixGenerator(prefixGenerator);
        }

        ToolContextToMcpMetaConverter metaConverter = metaConverterProvider
                .getIfUnique(ToolContextToMcpMetaConverter::defaultConverter);
        if (metaConverter != null) {
            builder.toolContextToMcpMetaConverter(metaConverter);
        }

        ToolCallback[] toolCallbacks = builder.build().getToolCallbacks();

        logger.info("🛠️  MCP Tool Registration Complete: {} tool callbacks registered", toolCallbacks.length);

        // Log details about each tool
        for (int i = 0; i < toolCallbacks.length; i++) {
            ToolCallback tool = toolCallbacks[i];
            String toolName = "unknown";
            String toolDescription = "no description";

            try {
                if (tool.getToolDefinition() != null) {
                    toolName = tool.getToolDefinition().name();
                    toolDescription = tool.getToolDefinition().description();
                }
            } catch (Exception e) {
                // Fallback if getToolDefinition fails
                logger.debug("Could not get tool definition for tool {}: {}", i + 1, e.getMessage());
            }

            logger.info("🔧 Tool {}: Name='{}', Description='{}', Type='{}'",
                       i + 1,
                       toolName,
                       toolDescription != null ? (toolDescription.length() > 100 ?
                           toolDescription.substring(0, 100) + "..." : toolDescription) : "no description",
                       tool.getClass().getSimpleName());
        }

        if (toolCallbacks.length == 0) {
            logger.warn("⚠️  No tools discovered from {} MCP clients - check server connectivity and tool definitions", clients.size());
        }

        return toolCallbacks;
    }
}

