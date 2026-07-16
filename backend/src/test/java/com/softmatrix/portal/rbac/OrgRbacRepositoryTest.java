package com.softmatrix.portal.rbac;

import com.softmatrix.portal.org.DepartmentRepository;
import com.softmatrix.portal.user.AppUserEntity;
import com.softmatrix.portal.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrgRbacRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired RoleRepository roles;
    @Autowired AppUserRepository users;
    @Autowired DepartmentRepository departments;

    @Test
    void seeds_root_department_and_builtin_roles() {
        assertThat(departments.findAll())
                .anyMatch(d -> d.getName().equals("总部") && d.getParentId() == null);
        assertThat(roles.findAll()).hasSize(4).allMatch(RoleEntity::isBuiltIn);
        assertThat(roles.findByName("Platform Admin").orElseThrow().getPermissions()).hasSize(9);
        assertThat(roles.findByName("Business User").orElseThrow().getPermissions())
                .containsExactlyInAnyOrder("AGENT_VIEW", "AGENT_RUN");
    }

    @Test
    void user_role_association_round_trips() {
        AppUserEntity u = new AppUserEntity();
        u.setUsername("alice");
        u.getRoles().add(roles.findByName("Auditor").orElseThrow());
        users.save(u);

        AppUserEntity loaded = users.findByUsername("alice").orElseThrow();
        assertThat(loaded.getRoles()).hasSize(1);
        assertThat(users.countByRoles_Id(loaded.getRoles().iterator().next().getId())).isEqualTo(1);
    }
}
