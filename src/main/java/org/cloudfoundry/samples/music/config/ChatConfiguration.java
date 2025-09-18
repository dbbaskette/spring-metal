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

    /**
     * ChatClient with MCP tool support using Spring AI 1.1.0 auto-configuration
     */
    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, @Autowired(required = false) ToolCallbackProvider tools) {
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

        return chatClientBuilder
            .defaultToolCallbacks(tools)
            .build();
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