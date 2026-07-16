package com.softmatrix.portal.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PositionRequest(@NotBlank @Size(max = 50) String name) {}
