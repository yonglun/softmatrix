package com.softmatrix.portal.auth;

import java.util.List;

public record UserInfo(String username, String name, List<String> permissions) {}
