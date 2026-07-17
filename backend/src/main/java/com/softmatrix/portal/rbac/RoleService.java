package com.softmatrix.portal.rbac;

import com.softmatrix.portal.common.ApiException;
import com.softmatrix.portal.rbac.dto.RoleRequest;
import com.softmatrix.portal.rbac.dto.RoleResponse;
import com.softmatrix.portal.user.AppUserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RoleService {

    private final RoleRepository repo;
    private final AppUserRepository users;

    public RoleService(RoleRepository repo, AppUserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    public List<RoleResponse> list() {
        return repo.findAll(Sort.by("name")).stream().map(this::toResponse).toList();
    }

    public RoleResponse create(RoleRequest req) {
        validatePermissions(req.permissions());
        if (repo.existsByName(req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_NAME_TAKEN", "角色名称已存在");
        }
        RoleEntity r = new RoleEntity();
        r.setName(req.name());
        r.setDescription(req.description());
        r.setBuiltIn(false);
        r.setPermissions(new HashSet<>(req.permissions()));
        return toResponse(repo.save(r));
    }

    public RoleResponse update(UUID id, RoleRequest req) {
        RoleEntity r = find(id);
        assertNotBuiltIn(r);
        validatePermissions(req.permissions());
        if (!r.getName().equals(req.name()) && repo.existsByName(req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_NAME_TAKEN", "角色名称已存在");
        }
        r.setName(req.name());
        r.setDescription(req.description());
        r.setPermissions(new HashSet<>(req.permissions()));
        return toResponse(repo.save(r));
    }

    public void delete(UUID id) {
        RoleEntity r = find(id);
        assertNotBuiltIn(r);
        if (users.countByRoles_Id(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_IN_USE", "该角色仍分配给用户,不能删除");
        }
        repo.delete(r);
    }

    private void validatePermissions(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERMISSION", "至少选择一项权限");
        }
        for (String p : permissions) {
            try {
                Permission.valueOf(p);
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERMISSION", "未知权限: " + p);
            }
        }
    }

    private void assertNotBuiltIn(RoleEntity r) {
        if (r.isBuiltIn()) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_BUILT_IN", "内置角色不可修改或删除");
        }
    }

    private RoleEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "角色不存在"));
    }

    private RoleResponse toResponse(RoleEntity r) {
        return new RoleResponse(r.getId(), r.getName(), r.getDescription(), r.isBuiltIn(),
                r.getPermissions().stream().sorted().toList(), users.countByRoles_Id(r.getId()));
    }
}
