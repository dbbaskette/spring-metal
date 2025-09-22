package org.cloudfoundry.samples.music.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;

import org.cloudfoundry.samples.music.domain.McpServerConnection;
import org.cloudfoundry.samples.music.repositories.jpa.McpServerConnectionRepository;
import org.cloudfoundry.samples.music.config.CloudFoundryMcpConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import org.springframework.boot.context.event.ApplicationReadyEvent;

@Service
@Profile("mcp")
@Transactional
public class McpServerConnectionService {

    private static final Logger logger = LoggerFactory.getLogger(McpServerConnectionService.class);

    private final McpServerConnectionRepository repository;
    private final DynamicMcpClientManager clientManager;

    public McpServerConnectionService(McpServerConnectionRepository repository,
            DynamicMcpClientManager clientManager) {
        this.repository = repository;
        this.clientManager = clientManager;
    }

    public List<McpServerConnection> listConnections() {
        List<McpServerConnection> connections = new ArrayList<>();

        // Add database-persisted connections
        connections.addAll(repository.findAll());

        // Add CF-bound ephemeral connections
        connections.addAll(CloudFoundryMcpConfiguration.getCfBoundConnections().values());

        return connections;
    }

    public McpServerConnection getConnection(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Connection with id %s not found".formatted(id)));
    }

    public McpServerConnection findByName(String name) {
        // First check database-persisted connections
        var dbConnection = repository.findByNameIgnoreCase(name).orElse(null);
        if (dbConnection != null) {
            return dbConnection;
        }

        // Then check CF-bound ephemeral connections
        return CloudFoundryMcpConfiguration.getCfBoundConnections().get(name);
    }

    public McpServerConnection createConnection(String name, String baseUrl, String endpoint, Boolean enabled,
            Map<String, String> headers) {
        Assert.hasText(name, "Connection name must not be empty");
        Assert.hasText(baseUrl, "Connection baseUrl must not be empty");

        // Check if name exists in database
        repository.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw new IllegalArgumentException("Connection name '%s' already exists".formatted(name));
        });

        // Check if name exists in CF-bound connections
        if (CloudFoundryMcpConfiguration.getCfBoundConnections().containsKey(name)) {
            throw new IllegalArgumentException("Connection name '%s' already exists as CF-bound service".formatted(name));
        }

        McpServerConnection connection = new McpServerConnection();
        connection.setName(name.trim());
        connection.setBaseUrl(baseUrl.trim());
        connection.setEndpoint(normalizeEndpoint(endpoint));
        connection.setEnabled(enabled == null || enabled);
        connection.setDefaultHeaders(headers);

        repository.save(connection);
        updateRuntimeRegistration(connection);
        return repository.save(connection);
    }

    public McpServerConnection updateConnection(UUID id, String name, String baseUrl, String endpoint, Boolean enabled,
            Map<String, String> headers) {
        McpServerConnection connection = getConnection(id);

        if (StringUtils.hasText(name) && !name.equalsIgnoreCase(connection.getName())) {
            repository.findByNameIgnoreCase(name).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("Connection name '%s' already exists".formatted(name));
                }
            });
            connection.setName(name.trim());
        }

        if (StringUtils.hasText(baseUrl)) {
            connection.setBaseUrl(baseUrl.trim());
        }

        if (endpoint != null) {
            connection.setEndpoint(normalizeEndpoint(endpoint));
        }

        if (headers != null) {
            connection.setDefaultHeaders(headers);
        }

        if (enabled != null) {
            connection.setEnabled(enabled);
        }

        updateRuntimeRegistration(connection);
        return repository.save(connection);
    }

    public McpServerConnection updateConnection(McpServerConnection connection) {
        updateRuntimeRegistration(connection);
        return repository.save(connection);
    }

    public McpServerConnection saveConnection(McpServerConnection connection) {
        if (connection.getEndpoint() != null) {
            connection.setEndpoint(normalizeEndpoint(connection.getEndpoint()));
        }
        McpServerConnection savedConnection = repository.save(connection);
        updateRuntimeRegistration(savedConnection);
        return savedConnection;
    }

    public void deleteConnection(UUID id) {
        McpServerConnection connection = getConnection(id);
        repository.delete(connection);
        clientManager.deregister(id);
    }

    public DynamicMcpClientManager.RegistrationResult testConnection(String name, String baseUrl, String endpoint,
            Map<String, String> headers) {
        Assert.hasText(name, "Connection name must not be empty");
        Assert.hasText(baseUrl, "Connection baseUrl must not be empty");

        McpServerConnection probe = new McpServerConnection();
        probe.setId(UUID.randomUUID());
        probe.setName(name.trim());
        probe.setBaseUrl(baseUrl.trim());
        probe.setEndpoint(normalizeEndpoint(endpoint));
        probe.setDefaultHeaders(headers);
        probe.setEnabled(true);

        return clientManager.testConnection(probe);
    }

    public void disableConnection(UUID id) {
        McpServerConnection connection = getConnection(id);
        connection.setEnabled(false);
        connection.markDisabled();
        repository.save(connection);
        clientManager.deregister(id);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeExistingConnections() {
        repository.findAll().forEach(connection -> {
            // Skip database connections if there's a CF-bound connection with the same name
            if (CloudFoundryMcpConfiguration.getCfBoundConnections().containsKey(connection.getName())) {
                logger.info("Skipping database connection '{}' as CF-bound connection exists with same name", connection.getName());
                return;
            }

            if (connection.isEnabled()) {
                try {
                    clientManager.register(connection);
                    connection.markSuccess();

                    // Update tool information after successful registration
                    updateToolInformation(connection);
                }
                catch (Exception ex) {
                    connection.markFailure(ex.getMessage());
                }
            }
            else {
                connection.markDisabled();
            }
            repository.save(connection);
        });
    }

    private void updateRuntimeRegistration(McpServerConnection connection) {
        if (connection.isEnabled()) {
            try {
                clientManager.register(connection);
                connection.markSuccess();

                // Update tool information after successful registration
                updateToolInformation(connection);
            }
            catch (Exception ex) {
                connection.markFailure(ex.getMessage());
            }
        }
        else {
            connection.markDisabled();
            clientManager.deregister(connection.getId());
        }
    }

    private void updateToolInformation(McpServerConnection connection) {
        try {
            List<String> availableTools = clientManager.getAvailableToolsForClient(connection.getId());
            connection.updateToolInformation(availableTools);
        } catch (Exception e) {
            // Tool discovery failure shouldn't prevent connection success
            // Log the warning but don't fail the connection
            connection.updateToolInformation(List.of());
        }
    }

    private String normalizeEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "/api/mcp";
        }
        String trimmed = endpoint.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }
}

