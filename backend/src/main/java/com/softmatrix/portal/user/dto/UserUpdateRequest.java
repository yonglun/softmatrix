package com.softmatrix.portal.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UserUpdateRequest(
        @Size(max = 100) String name,
        @Email @Size(max = 255) String email,
        UUID departmentId,
        UUID positionId) {}
