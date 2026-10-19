package dev.apisentinel.monitors;

import dev.apisentinel.checks.CheckResult;
import dev.apisentinel.monitors.MonitorDtos.CheckResponse;
import dev.apisentinel.monitors.MonitorDtos.CreateMonitorRequest;
import dev.apisentinel.monitors.MonitorDtos.MonitorResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitors")
public class MonitorController {
    private final MonitorService service;

    public MonitorController(MonitorService service) { this.service = service; }

    @GetMapping
    public List<MonitorResponse> list(@AuthenticationPrincipal UUID ownerId) { return service.list(ownerId); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonitorResponse create(@AuthenticationPrincipal UUID ownerId, @Valid @RequestBody CreateMonitorRequest input) {
        return service.create(ownerId, input);
    }

    @GetMapping("/{id}")
    public MonitorResponse get(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id) {
        return service.list(ownerId).stream().filter(monitor -> monitor.id().equals(id)).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found."));
    }

    @PatchMapping("/{id}")
    public MonitorResponse update(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id,
            @Valid @RequestBody CreateMonitorRequest input) {
        return service.update(ownerId, id, input);
    }

    @GetMapping("/{id}/checks")
    public List<CheckResponse> checks(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        List<CheckResult> checks = service.checks(ownerId, id, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return checks.stream().map(CheckResponse::from).toList();
    }

    @PostMapping("/{id}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pause(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id) { service.setEnabled(ownerId, id, false); }

    @PostMapping("/{id}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resume(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id) { service.setEnabled(ownerId, id, true); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id) { service.archive(ownerId, id); }
}