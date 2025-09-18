package org.cloudfoundry.samples.music.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpAsyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cloudfoundry.samples.music.domain.McpServerConnection;
import org.cloudfoundry.samples.music.service.support.McpConnectionException;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpAsyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("mcp")
public class DynamicMcpClientManager {

    private static final Logger logger = LoggerFactory.getLogger(DynamicMcpClientManager.class);

    private final McpClientCommonProperties commonProperties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<McpAsyncClientConfigurer> asyncClientConfigurerProvider;
    private final ObjectProvider<McpSyncClientConfigurer> syncClientConfigurerProvider;
    private final ObjectProvider<McpAsyncHttpClientRequestCustomizer> asyncHttpCustomizerProvider;
    private final ObjectProvider<McpSyncHttpClientRequestCustomizer> syncHttpCustomizerProvider;

    private final Map<UUID, ClientRegistration> registrations = new ConcurrentHashMap<>();
    private final Map<UUID, RetryState> retryStates = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);

    public DynamicMcpClientManager(McpClientCommonProperties commonProperties, ObjectMapper objectMapper,
            ObjectProvider<McpAsyncClientConfigurer> asyncClientConfigurerProvider,
            ObjectProvider<McpSyncClientConfigurer> syncClientConfigurerProvider,
            ObjectProvider<McpAsyncHttpClientRequestCustomizer> asyncHttpCustomizerProvider,
            ObjectProvider<McpSyncHttpClientRequestCustomizer> syncHttpCustomizerProvider) {
        this.commonProperties = commonProperties;
        this.objectMapper = objectMapper;
        this.asyncClientConfigurerProvider = asyncClientConfigurerProvider;
        this.syncClientConfigurerProvider = syncClientConfigurerProvider;
        this.asyncHttpCustomizerProvider = asyncHttpCustomizerProvider;
        this.syncHttpCustomizerProvider = syncHttpCustomizerProvider;
    }

    public RegistrationResult register(McpServerConnection connection) {
        lock.writeLock().lock();
        try {
            ClientRegistration existing = registrations.remove(connection.getId());
            if (existing != null) {
                existing.close();
            }

            if (!connection.isEnabled()) {
                // Clear any retry state for disabled connections
                retryStates.remove(connection.getId());
                return RegistrationResult.disabled(connection.getName());
            }

            return attemptRegistrationWithRetry(connection);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private RegistrationResult attemptRegistrationWithRetry(McpServerConnection connection) {
        try {
            ClientRegistration registration = buildRegistration(connection, commonProperties.isInitialized());
            registrations.put(connection.getId(), registration);

            // Clear retry state on successful connection
            retryStates.remove(connection.getId());

            logger.info("Successfully registered MCP connection '{}' at {}",
                       connection.getName(), registration.transportDescription());
            return RegistrationResult.connected(connection.getName(), registration.transportDescription());

        } catch (Exception ex) {
            logger.warn("Failed to register MCP connection '{}': {}", connection.getName(), ex.getMessage());

            // Check if this is a retryable error
            if (isRetryableException(ex)) {
                scheduleRetry(connection, ex);
                return RegistrationResult.retryScheduled(connection.getName(), ex.getMessage());
            } else {
                throw new McpConnectionException("Failed to register MCP connection '%s'".formatted(connection.getName()), ex);
            }
        }
    }

    private boolean isRetryableException(Throwable ex) {
        // Check for connection-related exceptions that should trigger retry
        String message = ex.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("connection refused") ||
                   lowerMessage.contains("connect exception") ||
                   lowerMessage.contains("timeout") ||
                   lowerMessage.contains("connection reset") ||
                   lowerMessage.contains("closed channel") ||
                   lowerMessage.contains("no route to host") ||
                   lowerMessage.contains("network unreachable");
        }

        // Check exception types
        return ex instanceof java.net.ConnectException ||
               ex instanceof java.net.SocketTimeoutException ||
               ex instanceof java.nio.channels.ClosedChannelException ||
               ex instanceof java.io.IOException;
    }

    private void scheduleRetry(McpServerConnection connection, Exception lastError) {
        RetryState retryState = retryStates.computeIfAbsent(connection.getId(),
                                                           k -> new RetryState(connection.getName()));

        if (retryState.shouldRetry()) {
            Duration delay = retryState.getNextRetryDelay();
            retryState.recordRetryAttempt(lastError.getMessage());

            logger.info("Scheduling retry {} for MCP connection '{}' in {} seconds",
                       retryState.attemptCount, connection.getName(), delay.getSeconds());

            retryExecutor.schedule(() -> {
                logger.info("Executing retry {} for MCP connection '{}'",
                           retryState.attemptCount, connection.getName());

                lock.writeLock().lock();
                try {
                    if (connection.isEnabled() && !registrations.containsKey(connection.getId())) {
                        attemptRegistrationWithRetry(connection);
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }, delay.getSeconds(), java.util.concurrent.TimeUnit.SECONDS);
        } else {
            logger.error("Max retry attempts ({}) exceeded for MCP connection '{}'. Last error: {}",
                        retryState.maxRetries, connection.getName(), lastError.getMessage());
            retryStates.remove(connection.getId());
        }
    }

    public void deregister(UUID connectionId) {
        lock.writeLock().lock();
        try {
            ClientRegistration registration = registrations.remove(connectionId);
            if (registration != null) {
                registration.close();
            }
            // Clear any pending retry state
            retryStates.remove(connectionId);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public RegistrationResult testConnection(McpServerConnection probe) {
        try {
            ClientRegistration registration = buildRegistration(probe, true);
            try {
                return RegistrationResult.connected(probe.getName(), registration.transportDescription());
            }
            finally {
                registration.close();
            }
        }
        catch (Exception ex) {
            throw new McpConnectionException("Failed to validate MCP connection '%s'".formatted(probe.getName()), ex);
        }
    }

    public Collection<McpAsyncClient> getActiveAsyncClients() {
        if (commonProperties.getType() != McpClientCommonProperties.ClientType.ASYNC) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            List<McpAsyncClient> clients = new ArrayList<>();
            for (ClientRegistration registration : registrations.values()) {
                if (registration.asyncClient() != null) {
                    clients.add(registration.asyncClient());
                }
            }
            return Collections.unmodifiableList(clients);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Collection<McpSyncClient> getActiveSyncClients() {
        if (commonProperties.getType() != McpClientCommonProperties.ClientType.SYNC) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            List<McpSyncClient> clients = new ArrayList<>();
            for (ClientRegistration registration : registrations.values()) {
                if (registration.syncClient() != null) {
                    clients.add(registration.syncClient());
                }
            }
            return Collections.unmodifiableList(clients);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Map<UUID, ClientRegistration> snapshot() {
        lock.readLock().lock();
        try {
            return Map.copyOf(registrations);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public List<String> getAvailableToolsForClient(UUID connectionId) {
        lock.readLock().lock();
        try {
            ClientRegistration registration = registrations.get(connectionId);
            if (registration == null) {
                return List.of();
            }

            if (registration.asyncClient() != null) {
                return getToolsFromAsyncClient(registration.asyncClient());
            } else if (registration.syncClient() != null) {
                return getToolsFromSyncClient(registration.syncClient());
            }

            return List.of();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    private List<String> getToolsFromAsyncClient(McpAsyncClient client) {
        try {
            var listToolsResult = client.listTools().block(commonProperties.getRequestTimeout());
            if (listToolsResult != null && listToolsResult.tools() != null) {
                return listToolsResult.tools().stream()
                    .map(tool -> tool.name())
                    .toList();
            }
        } catch (Exception e) {
            logger.warn("Failed to list tools from async MCP client: {}", e.getMessage());
        }
        return List.of();
    }

    private List<String> getToolsFromSyncClient(McpSyncClient client) {
        try {
            var listToolsResult = client.listTools();
            if (listToolsResult != null && listToolsResult.tools() != null) {
                return listToolsResult.tools().stream()
                    .map(tool -> tool.name())
                    .toList();
            }
        } catch (Exception e) {
            logger.warn("Failed to list tools from sync MCP client: {}", e.getMessage());
        }
        return List.of();
    }

    private ClientRegistration buildRegistration(McpServerConnection connection, boolean initialize) {
        HttpClientStreamableHttpTransport transport = buildTransport(connection);

        McpClientCommonProperties.ClientType clientType = commonProperties.getType();
        if (clientType == McpClientCommonProperties.ClientType.ASYNC) {
            McpAsyncClient asyncClient = createAsyncClient(connection, transport, initialize);
            return new ClientRegistration(connection, asyncClient, null, transportDescription(connection));
        }

        McpSyncClient syncClient = createSyncClient(connection, transport, initialize);
        return new ClientRegistration(connection, null, syncClient, transportDescription(connection));
    }

    private HttpClientStreamableHttpTransport buildTransport(McpServerConnection connection) {
        String baseUrl = connection.getBaseUrl();
        String endpoint = StringUtils.hasText(connection.getEndpoint()) ? connection.getEndpoint() : "/api/mcp";

        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport
                .builder(baseUrl)
                .endpoint(endpoint)
                .clientBuilder(HttpClient.newBuilder())
                .objectMapper(objectMapper);

        asyncHttpCustomizerProvider.ifUnique(builder::asyncHttpRequestCustomizer);
        syncHttpCustomizerProvider.ifUnique(builder::httpRequestCustomizer);

        return builder.build();
    }

    private McpAsyncClient createAsyncClient(McpServerConnection connection, HttpClientStreamableHttpTransport transport,
            boolean initialize) {
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(
                connectedClientName(connection.getName()), commonProperties.getVersion());

        McpClient.AsyncSpec spec = McpClient.async(transport)
                .clientInfo(clientInfo)
                .requestTimeout(commonProperties.getRequestTimeout());

        McpAsyncClientConfigurer configurer = asyncClientConfigurerProvider.getIfAvailable();
        if (configurer != null) {
            spec = configurer.configure(connection.getName(), spec);
        }

        McpAsyncClient client = spec.build();
        if (initialize) {
            client.initialize().block(commonProperties.getRequestTimeout());
        }
        return client;
    }

    private McpSyncClient createSyncClient(McpServerConnection connection, HttpClientStreamableHttpTransport transport,
            boolean initialize) {
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(
                connectedClientName(connection.getName()), connection.getName(), commonProperties.getVersion());

        McpClient.SyncSpec spec = McpClient.sync(transport)
                .clientInfo(clientInfo)
                .requestTimeout(commonProperties.getRequestTimeout());

        McpSyncClientConfigurer configurer = syncClientConfigurerProvider.getIfAvailable();
        if (configurer != null) {
            spec = configurer.configure(connection.getName(), spec);
        }

        McpSyncClient client = spec.build();
        if (initialize) {
            client.initialize();
        }
        return client;
    }

    private String connectedClientName(String connectionName) {
        return "%s - %s".formatted(commonProperties.getName(), connectionName);
    }

    private String transportDescription(McpServerConnection connection) {
        return "%s%s".formatted(connection.getBaseUrl(), connection.getEndpoint());
    }

    public record RegistrationResult(boolean success, String message) {
        public static RegistrationResult connected(String name, String location) {
            return new RegistrationResult(true, "Connected to %s via %s".formatted(name, location));
        }

        public static RegistrationResult disabled(String name) {
            return new RegistrationResult(false, "Connection '%s' disabled".formatted(name));
        }

        public static RegistrationResult retryScheduled(String name, String error) {
            return new RegistrationResult(false, "Connection '%s' failed, retry scheduled: %s".formatted(name, error));
        }
    }

    public record ClientRegistration(McpServerConnection source, McpAsyncClient asyncClient, McpSyncClient syncClient,
            String transportDescription) implements AutoCloseable {

        @Override
        public void close() {
            if (asyncClient != null) {
                asyncClient.close();
            }
            if (syncClient != null) {
                syncClient.close();
            }
        }

        boolean matchesConnection(UUID connectionId) {
            return Objects.equals(source.getId(), connectionId);
        }
    }

    private static class RetryState {
        private static final int DEFAULT_MAX_RETRIES = 5;
        private static final Duration INITIAL_DELAY = Duration.ofSeconds(5);
        private static final Duration MAX_DELAY = Duration.ofMinutes(5);

        final String connectionName;
        final int maxRetries;
        int attemptCount = 0;
        LocalDateTime lastAttempt;
        String lastError;

        RetryState(String connectionName) {
            this.connectionName = connectionName;
            this.maxRetries = DEFAULT_MAX_RETRIES;
        }

        boolean shouldRetry() {
            return attemptCount < maxRetries;
        }

        Duration getNextRetryDelay() {
            // Exponential backoff: 5s, 10s, 20s, 40s, 80s, capped at 5 minutes
            long delaySeconds = Math.min(INITIAL_DELAY.getSeconds() * (1L << attemptCount), MAX_DELAY.getSeconds());
            return Duration.ofSeconds(delaySeconds);
        }

        void recordRetryAttempt(String error) {
            attemptCount++;
            lastAttempt = LocalDateTime.now();
            lastError = error;
        }
    }
}

