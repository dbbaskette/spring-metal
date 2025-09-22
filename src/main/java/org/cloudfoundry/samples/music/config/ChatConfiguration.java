package org.cloudfoundry.samples.music.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

/**
 * Simple Chat Configuration using Spring AI 1.1.0 auto-configuration
 * Creates ChatClient with MCP tools when available
 */
@Configuration
@Profile({"llm", "mcp"})
public class ChatConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChatConfiguration.class);

    private ToolCallbackProvider toolCallbackProvider;

    public ChatConfiguration() {
        log.info("🏗️  ChatConfiguration constructor called - bean is being created");
    }

    /**
     * ChatClient with MCP tool support using Spring AI 1.1.0 auto-configuration
     */
    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, @Autowired(required = false) ToolCallbackProvider tools) {
        log.info("🚀 ChatClient bean method called! Creating ChatClient with tools...");
        log.info("🔧 Configuring MCP-enabled ChatClient with Spring AI 1.1.0");
        log.info("🛠️  ToolCallbackProvider available: {}", tools != null);

        // Store the tools for later inspection
        this.toolCallbackProvider = tools;

        if (tools != null) {
            log.info("✅ MCP tools will be integrated into ChatClient");
            try {
                log.info("🔍 Tool callback provider class: {}", tools.getClass().getSimpleName());
            } catch (Exception e) {
                log.debug("Could not get tool provider details: {}", e.getMessage());
            }
        } else {
            log.warn("⚠️  No MCP tools available - running without tool integration");
        }

        var chatClientBuilderWithTools = chatClientBuilder;

        if (tools != null) {
            try {
                var toolCallbacks = tools.getToolCallbacks();
                chatClientBuilderWithTools = chatClientBuilder.defaultToolCallbacks(toolCallbacks);
                log.info("🔧 Registered {} tool callbacks with ChatClient", toolCallbacks.length);

                // Log each tool for debugging
                for (int i = 0; i < toolCallbacks.length; i++) {
                    try {
                        var tool = toolCallbacks[i];
                        String toolName = tool.getToolDefinition() != null ? tool.getToolDefinition().name() : "unknown";
                        String toolDescription = tool.getToolDefinition() != null ? tool.getToolDefinition().description() : "no description";
                        log.info("🛠️  Tool [{}]: {} - {}", i + 1, toolName,
                                toolDescription.length() > 100 ? toolDescription.substring(0, 100) + "..." : toolDescription);
                    } catch (Exception e) {
                        log.warn("Could not inspect tool {}: {}", i + 1, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to register tools with ChatClient: {}", e.getMessage());
            }
        }

        return chatClientBuilderWithTools.build();
    }

    /**
     * Report MCP status after application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reportMcpStatus() {
        log.info("🎯 ========== SPRING METAL MCP STATUS ==========");
        log.info("🟢 Chat Configuration: ACTIVE");
        log.info("🌐 Transport: Streamable HTTP");

        // Report tool discovery status
        if (toolCallbackProvider != null) {
            try {
                String toolProviderClass = toolCallbackProvider.getClass().getSimpleName();
                log.info("🛠️  Tool Provider: {}", toolProviderClass);
                log.info("✅ MCP Tools: REGISTERED");
            } catch (Exception e) {
                log.warn("⚠️  Error inspecting tool provider: {}", e.getMessage());
                log.info("🛠️  MCP Tools: REGISTERED but not inspectable");
            }
        } else {
            log.warn("❌ MCP Tools: NONE DISCOVERED");
            log.warn("🔍 Possible issues:");
            log.warn("   - MCP server not running or not reachable");
            log.warn("   - MCP server not exposing tools correctly");
            log.warn("   - Network connectivity issues");
            log.warn("   - MCP client configuration problems");
        }

        log.info("🎯 =============================================");
    }
}