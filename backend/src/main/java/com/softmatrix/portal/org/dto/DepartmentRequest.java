package com.softmatrix.portal.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DepartmentRequest(
        @NotBlank @Size(max = 100) String name,
        UUID parentId,
        UUID managerUserId) {}
