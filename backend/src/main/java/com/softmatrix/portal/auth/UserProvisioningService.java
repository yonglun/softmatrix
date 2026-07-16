package com.softmatrix.portal.auth;

import com.softmatrix.portal.rbac.RoleRepository;
import com.softmatrix.portal.user.AppUserEntity;
import com.softmatrix.portal.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 登录成功时 JIT 镜像用户;bootstrap 用户首登自动获得 Platform Admin。 */
@Service
public class UserProvisioningService {

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final String bootstrapAdminUsername;

    public UserProvisioningService(AppUserRepository users, RoleRepository roles,
            @Value("${portal.bootstrap-admin-username:admin}") String bootstrapAdminUsername) {
        this.users = users;
        this.roles = roles;
        this.bootstrapAdminUsername = bootstrapAdminUsername;
    }

    @Transactional
    public void provision(String keycloakId, String username, String name, String email) {
        AppUserEntity u = users.findByUsername(username).orElseGet(() -> {
            AppUserEntity n = new AppUserEntity();
            n.setUsername(username);
            return n;
        });
        u.setKeycloakId(keycloakId);
        u.setName(name);
        u.setEmail(email);
        if (username.equals(bootstrapAdminUsername) && u.getRoles().isEmpty()) {
            roles.findByName("Platform Admin").ifPresent(r -> u.getRoles().add(r));
        }
        users.save(u);
    }
}
