package com.softmatrix.portal.user;

import com.softmatrix.portal.common.ApiException;
import com.softmatrix.portal.keycloak.KeycloakAdminClient;
import com.softmatrix.portal.org.DepartmentRepository;
import com.softmatrix.portal.org.PositionRepository;
import com.softmatrix.portal.rbac.RoleEntity;
import com.softmatrix.portal.rbac.RoleRepository;
import com.softmatrix.portal.user.dto.UserCreateRequest;
import com.softmatrix.portal.user.dto.UserResponse;
import com.softmatrix.portal.user.dto.UserUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class UserService {

    private final AppUserRepository repo;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final PositionRepository positions;
    private final KeycloakAdminClient kc;

    public UserService(AppUserRepository repo, RoleRepository roles,
                       DepartmentRepository departments, PositionRepository positions,
                       KeycloakAdminClient kc) {
        this.repo = repo;
        this.roles = roles;
        this.departments = departments;
        this.positions = positions;
        this.kc = kc;
    }

    public List<UserResponse> list(UUID departmentId, String keyword, Boolean enabled) {
        List<AppUserEntity> all = departmentId == null
                ? repo.findAllByOrderByUsername()
                : repo.findByDepartmentIdOrderByUsername(departmentId);
        String kw = keyword == null ? null : keyword.toLowerCase();
        return all.stream()
                .filter(u -> kw == null
                        || u.getUsername().toLowerCase().contains(kw)
                        || (u.getName() != null && u.getName().toLowerCase().contains(kw)))
                .filter(u -> enabled == null || u.isEnabled() == enabled)
                .map(this::toResponse)
                .toList();
    }

    /** 先 Keycloak、后门户库;KC 失败不落库(设计文档 §4.3)。 */
    @Transactional
    public UserResponse create(UserCreateRequest req) {
        validateRefs(req.departmentId(), req.positionId());
        List<RoleEntity> assigned = resolveRoles(req.roleIds());
        String kcId = kc.createUser(req.username(), req.name(), req.email(), req.password());
        AppUserEntity u = new AppUserEntity();
        u.setKeycloakId(kcId);
        u.setUsername(req.username());
        u.setName(req.name());
        u.setEmail(req.email());
        u.setDepartmentId(req.departmentId());
        u.setPositionId(req.positionId());
        u.getRoles().addAll(assigned);
        return toResponse(repo.save(u));
    }

    /** 姓名/邮箱写通 KC;部门/岗位仅门户侧。 */
    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest req) {
        AppUserEntity u = find(id);
        validateRefs(req.departmentId(), req.positionId());
        kc.updateUser(u.getKeycloakId(), req.name(), req.email());
        u.setName(req.name());
        u.setEmail(req.email());
        u.setDepartmentId(req.departmentId());
        u.setPositionId(req.positionId());
        return toResponse(repo.save(u));
    }

    @Transactional
    public UserResponse setEnabled(UUID id, boolean enabled, String actorUsername) {
        AppUserEntity u = find(id);
        if (!enabled && u.getUsername().equals(actorUsername)) {
            throw new ApiException(HttpStatus.CONFLICT, "SELF_LOCKOUT", "不能停用自己");
        }
        kc.setEnabled(u.getKeycloakId(), enabled);
        u.setEnabled(enabled);
        return toResponse(repo.save(u));
    }

    public void resetPassword(UUID id, String password) {
        AppUserEntity u = find(id);
        kc.resetPassword(u.getKeycloakId(), password);
    }

    @Transactional
    public UserResponse setRoles(UUID id, Set<UUID> roleIds, String actorUsername) {
        AppUserEntity u = find(id);
        List<RoleEntity> newRoles = resolveRoles(roleIds);
        if (u.getUsername().equals(actorUsername)) {
            boolean hadPlatformAdmin = u.getRoles().stream()
                    .anyMatch(r -> "Platform Admin".equals(r.getName()));
            boolean keepsPlatformAdmin = newRoles.stream()
                    .anyMatch(r -> "Platform Admin".equals(r.getName()));
            if (hadPlatformAdmin && !keepsPlatformAdmin) {
                throw new ApiException(HttpStatus.CONFLICT, "SELF_LOCKOUT",
                        "不能移除自己的 Platform Admin 角色");
            }
        }
        u.getRoles().clear();
        u.getRoles().addAll(newRoles);
        return toResponse(repo.save(u));
    }

    private List<RoleEntity> resolveRoles(Set<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return List.of();
        List<RoleEntity> found = roles.findAllById(roleIds);
        if (found.size() != roleIds.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "存在无效的角色 id");
        }
        return found;
    }

    private void validateRefs(UUID departmentId, UUID positionId) {
        if (departmentId != null && departments.findById(departmentId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DEPT_NOT_FOUND", "部门不存在");
        }
        if (positionId != null && positions.findById(positionId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "POSITION_NOT_FOUND", "岗位不存在");
        }
    }

    private AppUserEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
    }

    private UserResponse toResponse(AppUserEntity u) {
        String deptName = u.getDepartmentId() == null ? null
                : departments.findById(u.getDepartmentId())
                        .map(d -> d.getName()).orElse(null);
        String posName = u.getPositionId() == null ? null
                : positions.findById(u.getPositionId())
                        .map(p -> p.getName()).orElse(null);
        return new UserResponse(u.getId(), u.getUsername(), u.getName(), u.getEmail(),
                u.isEnabled(), u.getDepartmentId(), deptName, u.getPositionId(), posName,
                u.getRoles().stream()
                        .map(r -> new UserResponse.RoleBrief(r.getId(), r.getName()))
                        .sorted(Comparator.comparing(UserResponse.RoleBrief::name))
                        .toList());
    }
}
