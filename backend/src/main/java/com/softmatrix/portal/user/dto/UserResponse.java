package com.softmatrix.portal.user.dto;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String name,
        String email,
        boolean enabled,
        UUID departmentId,
        String departmentName,
        UUID positionId,
        String positionName,
        List<RoleBrief> roles) {
    public record RoleBrief(UUID id, String name) {}
}
