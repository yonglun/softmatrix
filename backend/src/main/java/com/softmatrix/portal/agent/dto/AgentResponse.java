package com.softmatrix.portal.agent.dto;

import com.softmatrix.portal.agent.AgentEntity;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentResponse(
        UUID id,
        String name,
        String description,
        String flowiseChatflowId,
        String owner,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AgentResponse from(AgentEntity e) {
        return new AgentResponse(e.getId(), e.getName(), e.getDescription(),
                e.getFlowiseChatflowId(), e.getOwner(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
