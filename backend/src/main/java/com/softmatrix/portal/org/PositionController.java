package com.softmatrix.portal.org;

import com.softmatrix.portal.org.dto.PositionRequest;
import com.softmatrix.portal.org.dto.PositionResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final PositionService service;

    public PositionController(PositionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('ORG_VIEW')")
    public List<PositionResponse> list() { return service.list(); }

    @PostMapping
    @PreAuthorize("@perm.has('ORG_MANAGE')")
    public PositionResponse create(@Valid @RequestBody PositionRequest req) {
        return service.create(req.name());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('ORG_MANAGE')")
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
