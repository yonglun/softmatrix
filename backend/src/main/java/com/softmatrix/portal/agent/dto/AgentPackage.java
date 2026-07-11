package com.softmatrix.portal.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record AgentPackage(
        String softmatrixVersion,
        AgentMeta agent,
        FlowMeta flow
) {
    public record AgentMeta(String name, String description, String category, List<String> tags) {}
    public record FlowMeta(String name, JsonNode flowData) {}
}
