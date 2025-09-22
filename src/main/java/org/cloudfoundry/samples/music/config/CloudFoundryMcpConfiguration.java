package org.cloudfoundry.samples.music.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cloudfoundry.samples.music.domain.McpServerConnection;
import org.cloudfoundry.samples.music.service.McpServerConnectionService;
import org.cloudfoundry.samples.music.service.DynamicMcpClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@ConditionalOnCloudPlatform(CloudPlatform.CLOUD_FOUNDRY)
public class CloudFoundryMcpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CloudFoundryMcpConfiguration.class);

    @Autowired
    private Environment environment;

    @Autowired
    private McpServerConnectionService mcpServerConnectionService;

    @Autowired
    private DynamicMcpClientManager clientManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Store CF-bound MCP connections in memory (not persisted to database)
    private static final Map<String, McpServerConnection> cfBoundConnections = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeMcpFromVcapServices() {
        // Clear any previous CF-bound connections from memory
        cfBoundConnections.clear();
        String vcapServices = environment.getProperty("VCAP_SERVICES");

        if (vcapServices == null || vcapServices.isEmpty()) {
            logger.debug("No VCAP_SERVICES found, skipping MCP service binding configuration");
            return;
        }

        try {
            JsonNode vcapNode = objectMapper.readTree(vcapServices);

            JsonNode userProvidedServices = vcapNode.get("user-provided");
            if (userProvidedServices != null && userProvidedServices.isArray()) {
                for (JsonNode service : userProvidedServices) {
                    if (isMcpService(service)) {
                        configureMcpFromService(service);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse VCAP_SERVICES for MCP configuration", e);
        }
    }

    private boolean isMcpService(JsonNode service) {
        JsonNode name = service.get("name");
        JsonNode tags = service.get("tags");

        // Check service name for MCP indicators
        if (name != null) {
            String serviceName = name.asText().toLowerCase();
            if (serviceName.contains("mcp") || serviceName.contains("model-context-protocol")) {
                return true;
            }
        }

        // Check tags for MCP indicators
        if (tags != null && tags.isArray()) {
            for (JsonNode tag : tags) {
                String tagValue = tag.asText().toLowerCase();
                if (tagValue.contains("mcp") || tagValue.contains("model-context-protocol")) {
                    return true;
                }
            }
        }

        // Check if credentials have endpoint (URL is now optional)
        JsonNode credentials = service.get("credentials");
        if (credentials != null) {
            // Only require endpoint; URL can be derived
            return credentials.has("endpoint");
        }

        return false;
    }

    private void configureMcpFromService(JsonNode service) {
        try {
            JsonNode credentials = service.get("credentials");
            if (credentials == null) {
                logger.warn("MCP service found but no credentials provided");
                return;
            }

            String endpoint = credentials.has("endpoint") ? credentials.get("endpoint").asText() : null;
            if (endpoint == null) {
                logger.warn("MCP service found but missing required endpoint in credentials");
                return;
            }

            String serviceName = service.has("name") ? service.get("name").asText() : "CF-Bound-MCP-Server";

            // Derive base URL with multiple fallback options
            String baseUrl = deriveBaseUrl(credentials, serviceName);

            // Create CF-bound connection (in-memory only, not persisted)
            logger.info("Creating ephemeral MCP server connection from CF binding: {}", serviceName);
            McpServerConnection cfConnection = new McpServerConnection();
            cfConnection.setId(UUID.randomUUID()); // Generate UUID for CF-bound connections
            cfConnection.setName(serviceName);
            cfConnection.setBaseUrl(baseUrl);
            cfConnection.setEndpoint(endpoint);
            cfConnection.setEnabled(true);

            Map<String, String> headers = parseHeaders(credentials);
            if (!headers.isEmpty()) {
                cfConnection.setDefaultHeaders(headers);
            }

            // Store in memory for UI display
            cfBoundConnections.put(serviceName, cfConnection);

            // Register directly with client manager for runtime use
            try {
                clientManager.register(cfConnection);
                cfConnection.markSuccess();
                logger.info("Successfully registered CF-bound MCP server: {}", serviceName);
            } catch (Exception ex) {
                cfConnection.markFailure(ex.getMessage());
                logger.warn("Failed to register CF-bound MCP server '{}': {}", serviceName, ex.getMessage());
            }

            logger.info("Successfully configured ephemeral MCP server from Cloud Foundry service binding: {}", serviceName);

        } catch (Exception e) {
            logger.error("Failed to configure MCP from Cloud Foundry service", e);
        }
    }

    private String deriveBaseUrl(JsonNode credentials, String serviceName) {
        // Priority 1: Explicit URL provided
        if (credentials.has("url")) {
            String url = credentials.get("url").asText();
            logger.debug("Using explicit URL from credentials: {}", url);
            return url;
        }

        // Priority 2: Hostname and optional port provided
        if (credentials.has("hostname")) {
            String hostname = credentials.get("hostname").asText();
            String port = credentials.has("port") ? credentials.get("port").asText() : "8080";
            String protocol = credentials.has("protocol") ? credentials.get("protocol").asText() : "http";
            String url = String.format("%s://%s.apps.internal:%s", protocol, hostname, port);
            logger.info("Derived URL from hostname: {}", url);
            return url;
        }

        // Priority 3: Derive from service name (assumes app is deployed with same name)
        if (serviceName != null && !serviceName.equals("CF-Bound-MCP-Server")) {
            String port = credentials.has("port") ? credentials.get("port").asText() : "8080";
            String protocol = credentials.has("protocol") ? credentials.get("protocol").asText() : "http";
            String url = String.format("%s://%s.apps.internal:%s", protocol, serviceName, port);
            logger.info("Auto-derived URL from service name '{}': {}", serviceName, url);
            return url;
        }

        // Fallback: Use localhost (shouldn't normally reach here)
        logger.warn("Could not derive URL from service binding, using localhost fallback");
        return "http://localhost:8080";
    }

    private Map<String, String> parseHeaders(JsonNode credentials) {
        Map<String, String> headers = new HashMap<>();

        JsonNode headersNode = credentials.get("headers");
        if (headersNode != null && headersNode.isObject()) {
            headersNode.fields().forEachRemaining(entry -> {
                headers.put(entry.getKey(), entry.getValue().asText());
            });
        }

        if (credentials.has("apiKey")) {
            headers.put("Authorization", "Bearer " + credentials.get("apiKey").asText());
        }

        return headers;
    }

    /**
     * Get all CF-bound MCP connections (ephemeral, not persisted to database)
     */
    public static Map<String, McpServerConnection> getCfBoundConnections() {
        return new HashMap<>(cfBoundConnections);
    }
}