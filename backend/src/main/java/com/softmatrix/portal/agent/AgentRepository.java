package com.softmatrix.portal.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {
}
