package com.softmatrix.portal.auth;

import com.softmatrix.portal.rbac.PermissionChecker;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final PermissionChecker permissionChecker;

    public MeController(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @GetMapping("/api/me")
    public UserInfo me(@AuthenticationPrincipal OidcUser principal) {
        var permissions = permissionChecker.loadPermissions(principal.getPreferredUsername())
                .stream().sorted().toList();
        return new UserInfo(principal.getPreferredUsername(), principal.getFullName(), permissions);
    }
}
