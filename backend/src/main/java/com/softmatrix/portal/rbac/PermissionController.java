package com.softmatrix.portal.rbac;

import com.softmatrix.portal.rbac.dto.PermissionResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class PermissionController {

    /** 权限目录,角色编辑页的勾选数据源。 */
    @GetMapping("/api/permissions")
    @PreAuthorize("@perm.has('ROLE_VIEW')")
    public List<PermissionResponse> list() {
        return Arrays.stream(Permission.values())
                .map(p -> new PermissionResponse(p.name(), p.getLabel(), p.getGroup()))
                .toList();
    }
}
