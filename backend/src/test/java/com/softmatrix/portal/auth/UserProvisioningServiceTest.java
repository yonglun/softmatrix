package com.softmatrix.portal.auth;

import com.softmatrix.portal.rbac.RoleEntity;
import com.softmatrix.portal.rbac.RoleRepository;
import com.softmatrix.portal.user.AppUserEntity;
import com.softmatrix.portal.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserProvisioningServiceTest {

    AppUserRepository users = mock(AppUserRepository.class);
    RoleRepository roles = mock(RoleRepository.class);
    UserProvisioningService service;

    RoleEntity platformAdmin = new RoleEntity();

    @BeforeEach
    void setUp() {
        platformAdmin.setName("Platform Admin");
        when(roles.findByName("Platform Admin")).thenReturn(Optional.of(platformAdmin));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new UserProvisioningService(users, roles, "admin");
    }

    @Test
    void first_login_creates_mirror_row() {
        when(users.findByUsername("alice")).thenReturn(Optional.empty());

        service.provision("kc-9", "alice", "Alice", "a@x.com");

        ArgumentCaptor<AppUserEntity> cap = ArgumentCaptor.forClass(AppUserEntity.class);
        verify(users).save(cap.capture());
        assertThat(cap.getValue().getUsername()).isEqualTo("alice");
        assertThat(cap.getValue().getKeycloakId()).isEqualTo("kc-9");
        assertThat(cap.getValue().getRoles()).isEmpty();
    }

    @Test
    void bootstrap_admin_gets_platform_admin_role_once() {
        when(users.findByUsername("admin")).thenReturn(Optional.empty());

        service.provision("kc-1", "admin", "Admin", "admin@x.com");

        ArgumentCaptor<AppUserEntity> cap = ArgumentCaptor.forClass(AppUserEntity.class);
        verify(users).save(cap.capture());
        assertThat(cap.getValue().getRoles()).containsExactly(platformAdmin);
    }

    @Test
    void existing_user_profile_is_refreshed_roles_untouched() {
        AppUserEntity existing = new AppUserEntity();
        existing.setUsername("admin");
        existing.getRoles().add(platformAdmin);
        when(users.findByUsername("admin")).thenReturn(Optional.of(existing));

        service.provision("kc-1", "admin", "新名字", "new@x.com");

        ArgumentCaptor<AppUserEntity> cap = ArgumentCaptor.forClass(AppUserEntity.class);
        verify(users).save(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("新名字");
        assertThat(cap.getValue().getRoles()).hasSize(1);
    }
}
