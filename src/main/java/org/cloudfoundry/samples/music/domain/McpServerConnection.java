package org.cloudfoundry.samples.music.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "mcp_server_connections")
public class McpServerConnection {
    
    public enum ConnectionStatus {
        NEW,
        ACTIVE,
        DISABLED,
        ERROR
    }

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "connection_name", nullable = false, unique = true)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "enabled")
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConnectionStatus status = ConnectionStatus.NEW;

    @Column(name = "last_success_at")
    private Instant lastSuccessfulAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "last_error_message", length = 1024)
    private String lastErrorMessage;

    @ElementCollection
    @CollectionTable(name = "mcp_server_connection_headers", joinColumns = @JoinColumn(name = "connection_id"))
    @MapKeyColumn(name = "header_name")
    @Column(name = "header_value")
    private Map<String, String> defaultHeaders = new HashMap<>();

    @Column(name = "tool_count")
    private Integer toolCount = 0;

    @Column(name = "available_tools", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> availableTools = new ArrayList<>();

    public McpServerConnection() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectionStatus status) {
        this.status = status;
    }

    public Instant getLastSuccessfulAt() {
        return lastSuccessfulAt;
    }

    public void setLastSuccessfulAt(Instant lastSuccessfulAt) {
        this.lastSuccessfulAt = lastSuccessfulAt;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public void setLastFailureAt(Instant lastFailureAt) {
        this.lastFailureAt = lastFailureAt;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public Map<String, String> getDefaultHeaders() {
        return defaultHeaders;
    }

    public void setDefaultHeaders(Map<String, String> defaultHeaders) {
        this.defaultHeaders = defaultHeaders != null ? new HashMap<>(defaultHeaders) : new HashMap<>();
    }

    public void markSuccess() {
        this.status = ConnectionStatus.ACTIVE;
        this.lastSuccessfulAt = Instant.now();
        this.lastErrorMessage = null;
    }

    public void markDisabled() {
        this.status = ConnectionStatus.DISABLED;
    }

    public void markFailure(String message) {
        this.status = ConnectionStatus.ERROR;
        this.lastFailureAt = Instant.now();
        this.lastErrorMessage = message;
    }

    public Integer getToolCount() {
        return toolCount;
    }

    public void setToolCount(Integer toolCount) {
        this.toolCount = toolCount != null ? toolCount : 0;
    }

    public List<String> getAvailableTools() {
        return availableTools != null ? availableTools : new ArrayList<>();
    }

    public void setAvailableTools(List<String> availableTools) {
        this.availableTools = availableTools != null ? new ArrayList<>(availableTools) : new ArrayList<>();
        this.toolCount = this.availableTools.size();
    }

    public void updateToolInformation(List<String> tools) {
        this.availableTools = tools != null ? new ArrayList<>(tools) : new ArrayList<>();
        this.toolCount = this.availableTools.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof McpServerConnection that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

