package com.softmatrix.portal.org;

import com.softmatrix.portal.org.dto.DepartmentNode;
import com.softmatrix.portal.org.dto.DepartmentRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('ORG_VIEW')")
    public List<DepartmentNode> tree() { return service.tree(); }

    @PostMapping
    @PreAuthorize("@perm.has('ORG_MANAGE')")
    public DepartmentNode create(@Valid @RequestBody DepartmentRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('ORG_MANAGE')")
    public DepartmentNode update(@PathVariable UUID id, @Valid @RequestBody DepartmentRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('ORG_MANAGE')")
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
