package dev.apisentinel.incidents;

import dev.apisentinel.monitors.MonitorDtos.IncidentResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncidentService {
    private final IncidentRepository incidents;

    public IncidentService(IncidentRepository incidents) { this.incidents = incidents; }

    @Transactional(readOnly = true)
    public List<IncidentResponse> list(UUID ownerId) {
        return incidents.findByMonitorOwnerIdOrderByStartedAtDesc(ownerId).stream().map(IncidentResponse::from).toList();
    }

    @Transactional
    public IncidentResponse acknowledge(UUID ownerId, UUID incidentId) {
        Incident incident = incidents.findByIdAndMonitorOwnerId(incidentId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        if ("RESOLVED".equals(incident.getState())) throw new ResponseStatusException(HttpStatus.CONFLICT, "A resolved incident cannot be acknowledged.");
        incident.acknowledge(Instant.now());
        return IncidentResponse.from(incident);
    }
}