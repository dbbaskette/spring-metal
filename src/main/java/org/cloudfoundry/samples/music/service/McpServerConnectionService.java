package org.cloudfoundry.samples.music.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.cloudfoundry.samples.music.domain.McpServerConnection;
import org.cloudfoundry.samples.music.repositories.jpa.McpServerConnectionRepository;
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

    private final McpServerConnectionRepository repository;
    private final DynamicMcpClientManager clientManager;

    public McpServerConnectionService(McpServerConnectionRepository repository,
            DynamicMcpClientManager clientManager) {
        this.repository = repository;
        this.clientManager = clientManager;
    }

    public List<McpServerConnection> listConnections() {
        return repository.findAll();
    }

    public McpServerConnection getConnection(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Connection with id %s not found".formatted(id)));
    }

    public McpServerConnection createConnection(String name, String baseUrl, String endpoint, Boolean enabled,
            Map<String, String> headers) {
        Assert.hasText(name, "Connection name must not be empty");
        Assert.hasText(baseUrl, "Connection baseUrl must not be empty");

        repository.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw new IllegalArgumentException("Connection name '%s' already exists".formatted(name));
        });

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

