package com.softmatrix.portal.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AgentRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @Size(max = 50) String category,
        List<String> tags,
        // 仅创建时使用;更新时忽略。可空,create 时在 Service 中校验非空。
        @Size(max = 64) String flowiseChatflowId
) {}
