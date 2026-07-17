package com.softmatrix.portal.auth;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/** OIDC 登录钩子:加载用户后先做 JIT 镜像再返回。 */
@Component
public class CustomOidcUserService extends OidcUserService {

    private final UserProvisioningService provisioning;

    public CustomOidcUserService(UserProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = super.loadUser(userRequest);
        provisioning.provision(user.getSubject(), user.getPreferredUsername(),
                user.getFullName(), user.getEmail());
        return user;
    }
}
