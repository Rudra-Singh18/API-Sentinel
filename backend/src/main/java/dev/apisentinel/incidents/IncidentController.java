package dev.apisentinel.incidents;

import dev.apisentinel.monitors.MonitorDtos.IncidentResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {
    private final IncidentService service;

    public IncidentController(IncidentService service) { this.service = service; }

    @GetMapping
    public List<IncidentResponse> list(@AuthenticationPrincipal UUID ownerId) { return service.list(ownerId); }

    @PostMapping("/{id}/acknowledge")
    @ResponseStatus(HttpStatus.OK)
    public IncidentResponse acknowledge(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id) {
        return service.acknowledge(ownerId, id);
    }
}