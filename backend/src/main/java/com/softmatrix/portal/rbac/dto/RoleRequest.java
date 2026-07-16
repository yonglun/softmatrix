package com.softmatrix.portal.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record RoleRequest(
        @NotBlank @Size(max = 50) String name,
        String description,
        Set<String> permissions) {}
