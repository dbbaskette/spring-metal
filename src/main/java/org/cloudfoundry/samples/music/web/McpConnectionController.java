package org.cloudfoundry.samples.music.web;

import java.util.List;
import java.util.Map;

import java.util.UUID;

import org.cloudfoundry.samples.music.service.support.McpConnectionException;

import org.cloudfoundry.samples.music.domain.McpServerConnection;
import org.cloudfoundry.samples.music.service.McpServerConnectionService;
import org.cloudfoundry.samples.music.web.dto.McpConnectionRequest;
import org.cloudfoundry.samples.music.web.dto.McpConnectionResponse;
import org.cloudfoundry.samples.music.web.dto.McpConnectionTestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("mcp")
@RequestMapping("/api/mcp/connections")
public class McpConnectionController {

    private final McpServerConnectionService connectionService;

    public McpConnectionController(McpServerConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public List<McpConnectionResponse> listConnections() {
        return connectionService.listConnections().stream().map(McpConnectionResponse::fromEntity).toList();
    }

    @PostMapping
    public McpConnectionResponse createConnection(@RequestBody McpConnectionRequest request) {
        try {
            McpServerConnection entity = connectionService.createConnection(request.name(), request.baseUrl(),
                    request.endpoint(), request.enabled(), request.headers());
            return McpConnectionResponse.fromEntity(entity);
        }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/{id}")
    public McpConnectionResponse updateConnection(@PathVariable("id") UUID id,
            @RequestBody McpConnectionRequest request) {
        try {
            McpServerConnection entity = connectionService.updateConnection(id, request.name(), request.baseUrl(),
                    request.endpoint(), request.enabled(), request.headers());
            return McpConnectionResponse.fromEntity(entity);
        }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConnection(@PathVariable("id") UUID id) {
        connectionService.deleteConnection(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disable")
    public McpConnectionResponse disableConnection(@PathVariable("id") UUID id) {
        connectionService.disableConnection(id);
        return McpConnectionResponse.fromEntity(connectionService.getConnection(id));
    }

    @PostMapping("/test")
    public McpConnectionTestResponse testConnection(@RequestBody McpConnectionRequest request) {
        try {
            var result = connectionService.testConnection(request.name(), request.baseUrl(), request.endpoint(),
                    request.headers());
            return new McpConnectionTestResponse(result.success(), result.message());
        }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        catch (McpConnectionException ex) {
            return new McpConnectionTestResponse(false, ex.getMessage());
        }
    }

    @ExceptionHandler(McpConnectionException.class)
    public ResponseEntity<Map<String, String>> handleConnectionError(McpConnectionException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "MCP connection error"));
    }
}
