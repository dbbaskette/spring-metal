package org.cloudfoundry.samples.music.service.support;

public class McpConnectionException extends RuntimeException {

    public McpConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

