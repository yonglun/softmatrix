package com.softmatrix.portal.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public UserInfo me(@AuthenticationPrincipal OidcUser principal) {
        return new UserInfo(
                principal.getPreferredUsername(),
                principal.getFullName());
    }
}
