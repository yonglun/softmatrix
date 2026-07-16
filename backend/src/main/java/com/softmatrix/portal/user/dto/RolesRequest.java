package com.softmatrix.portal.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record RolesRequest(@NotNull Set<UUID> roleIds) {}
