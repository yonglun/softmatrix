package com.softmatrix.portal.agent.dto;

import com.softmatrix.portal.agent.AgentEntity;
import com.softmatrix.portal.agent.AgentStatus;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record AgentResponse(
        UUID id,
        String name,
        String description,
        String category,
        List<String> tags,
        String flowiseChatflowId,
        AgentStatus status,
        String owner,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AgentResponse from(AgentEntity e) {
        return new AgentResponse(
                e.getId(), e.getName(), e.getDescription(), e.getCategory(),
                e.getTags() == null ? List.of() : Arrays.asList(e.getTags()),
                e.getFlowiseChatflowId(), e.getStatus(), e.getOwner(),
                e.getPublishedAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
