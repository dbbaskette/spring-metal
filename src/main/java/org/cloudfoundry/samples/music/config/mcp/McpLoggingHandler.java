package org.cloudfoundry.samples.music.config.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springaicommunity.mcp.annotation.McpLogging;
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;

/**
 * Handler for receiving log messages from MCP servers.
 * This allows us to see what the MCP servers are doing internally.
 */
@Component
@Profile("mcp")
public class McpLoggingHandler {

    private static final Logger logger = LoggerFactory.getLogger(McpLoggingHandler.class);

    /**
     * Handles log messages from all MCP servers.
     * This will receive any log notifications that MCP servers send to the client.
     */
    @McpLogging(clients = "*")
    public void handleServerLogMessage(LoggingLevel level, String loggerName, String data) {
        // Format the log message with server context
        String message = String.format("[MCP-Server: %s] %s",
                                      loggerName != null ? loggerName : "Unknown",
                                      data);

        // Log at appropriate level based on server's log level
        if (level != null) {
            switch (level) {
                case LoggingLevel.ERROR:
                    logger.error("🚨 {}", message);
                    break;
                case LoggingLevel.INFO:
                    logger.info("📡 {}", message);
                    break;
                case LoggingLevel.DEBUG:
                    logger.debug("🔍 {}", message);
                    break;
                default:
                    logger.info("📡 {}", message);
                    break;
            }
        } else {
            logger.info("📡 {}", message);
        }
    }
}