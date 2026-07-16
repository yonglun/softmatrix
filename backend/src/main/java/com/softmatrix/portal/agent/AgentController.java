package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('AGENT_VIEW')")
    public List<AgentResponse> list(@RequestParam(required = false) String category,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String tag) {
        return service.list(category, status, keyword, tag);
    }

    @GetMapping("/categories")
    @PreAuthorize("@perm.has('AGENT_VIEW')")
    public List<String> categories() { return service.listCategories(); }

    @GetMapping("/tags")
    @PreAuthorize("@perm.has('AGENT_VIEW')")
    public List<String> tags() { return service.listTags(); }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('AGENT_VIEW')")
    public AgentResponse get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("@perm.has('AGENT_MANAGE')")
    public AgentResponse create(@Valid @RequestBody AgentRequest req,
                                @AuthenticationPrincipal OidcUser user) {
        return service.create(req, user.getPreferredUsername());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('AGENT_MANAGE')")
    public AgentResponse update(@PathVariable UUID id, @Valid @RequestBody AgentRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('AGENT_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@perm.has('AGENT_PUBLISH')")
    public AgentResponse publish(@PathVariable UUID id) { return service.publish(id); }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@perm.has('AGENT_PUBLISH')")
    public AgentResponse disable(@PathVariable UUID id) { return service.disable(id); }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@perm.has('AGENT_PUBLISH')")
    public AgentResponse withdraw(@PathVariable UUID id) { return service.withdraw(id); }

    @GetMapping("/{id}/export")
    @PreAuthorize("@perm.has('AGENT_MANAGE')")
    public org.springframework.http.ResponseEntity<com.softmatrix.portal.agent.dto.AgentPackage>
            export(@PathVariable UUID id) {
        com.softmatrix.portal.agent.dto.AgentPackage pkg = service.export(id);
        String filename = "softmatrix-agent-" + id + ".json";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(pkg);
    }

    @PostMapping("/import")
    @PreAuthorize("@perm.has('AGENT_MANAGE')")
    public AgentResponse importAgent(
            @RequestBody com.softmatrix.portal.agent.dto.AgentPackage pkg,
            @AuthenticationPrincipal OidcUser user) {
        return service.importPackage(pkg, user.getPreferredUsername());
    }
}
