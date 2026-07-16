package com.softmatrix.portal.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UserCreateRequest(
        @NotBlank @Size(max = 100) String username,
        @Size(max = 100) String name,
        @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        UUID departmentId,
        UUID positionId,
        Set<UUID> roleIds) {}
