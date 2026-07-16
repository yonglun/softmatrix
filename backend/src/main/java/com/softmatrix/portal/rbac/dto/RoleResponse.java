package com.softmatrix.portal.rbac.dto;

import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        boolean builtIn,
        List<String> permissions,
        long userCount) {}
