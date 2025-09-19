package org.cloudfoundry.samples.music.repositories.jpa;

import java.util.Optional;
import java.util.UUID;

import org.cloudfoundry.samples.music.domain.McpServerConnection;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("mcp")
public interface McpServerConnectionRepository extends JpaRepository<McpServerConnection, UUID> {

    Optional<McpServerConnection> findByNameIgnoreCase(String name);
}
