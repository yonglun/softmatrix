package com.softmatrix.portal.org.dto;

import java.util.List;
import java.util.UUID;

public record DepartmentNode(
        UUID id,
        String name,
        UUID parentId,
        UUID managerUserId,
        String managerName,
        List<DepartmentNode> children) {}
