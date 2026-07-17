package com.softmatrix.portal.rbac;

import com.softmatrix.portal.rbac.dto.RoleRequest;
import com.softmatrix.portal.rbac.dto.RoleResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('ROLE_VIEW')")
    public List<RoleResponse> list() { return service.list(); }

    @PostMapping
    @PreAuthorize("@perm.has('ROLE_MANAGE')")
    public RoleResponse create(@Valid @RequestBody RoleRequest req) { return service.create(req); }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('ROLE_MANAGE')")
    public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('ROLE_MANAGE')")
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
