package org.cloudfoundry.samples.music.web.dto;

import java.util.Map;

public record McpConnectionRequest(String name, String baseUrl, String endpoint, Boolean enabled,
        Map<String, String> headers) {
}

