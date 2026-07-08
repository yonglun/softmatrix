package com.softmatrix.portal.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @NotBlank @Size(max = 64) String flowiseChatflowId
) {}
