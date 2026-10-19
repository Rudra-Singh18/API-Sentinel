package dev.apisentinel.checks;

import dev.apisentinel.incidents.Incident;
import dev.apisentinel.incidents.IncidentRepository;
import dev.apisentinel.monitors.Monitor;
import dev.apisentinel.monitors.MonitorRepository;
import dev.apisentinel.notifications.IncidentOpenedEvent;
import dev.apisentinel.notifications.IncidentRecoveredEvent;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorStateService {
    private final MonitorRepository monitors;
    private final CheckResultRepository checks;
    private final IncidentRepository incidents;
    private final ApplicationEventPublisher events;

    public MonitorStateService(MonitorRepository monitors, CheckResultRepository checks, IncidentRepository incidents, ApplicationEventPublisher events) {
        this.monitors = monitors; this.checks = checks; this.incidents = incidents; this.events = events;
    }

    @Transactional
    public void record(UUID monitorId, CheckOutcome outcome) {
        Monitor monitor = monitors.findById(monitorId).orElse(null);
        if (monitor == null || monitor.isArchived() || !monitor.isEnabled()) return;
        checks.save(new CheckResult(monitor, outcome.startedAt(), outcome.completedAt(),
                outcome.available() ? "AVAILABLE" : "UNAVAILABLE", outcome.httpStatus(), outcome.responseTimeMs(),
                outcome.failureCategory(), outcome.diagnosticMessage()));
        if (outcome.available()) recordSuccess(monitor, outcome);
        else recordFailure(monitor, outcome);
    }

    private void recordFailure(Monitor monitor, CheckOutcome outcome) {
        int failures = monitor.getConsecutiveFailures() + 1;
        monitor.setConsecutiveFailures(failures);
        monitor.setConsecutiveSuccesses(0);
        if (failures >= monitor.getFailureThreshold()) {
            monitor.setStatus(Monitor.Status.DOWN);
            String summary = outcome.diagnosticMessage() == null ? "Endpoint did not return the expected response." : outcome.diagnosticMessage();
            var incident = incidents.findFirstByMonitorIdAndStateInOrderByStartedAtDesc(monitor.getId(), List.of("OPEN", "ACKNOWLEDGED"));
            if (incident.isPresent()) incident.get().updateFailure(summary, outcome.failureCategory());
            else {
                Incident created = incidents.save(new Incident(monitor, summary, outcome.failureCategory(), outcome.startedAt()));
                if (monitor.getOwner().isIncidentEmailsEnabled()) {
                    events.publishEvent(new IncidentOpenedEvent(created.getId(), monitor.getOwner().getEmail(), monitor.getName(), host(monitor.getUrl()), summary, outcome.startedAt()));
                }
            }
        } else if (monitor.getStatus() != Monitor.Status.DOWN) {
            monitor.setStatus(Monitor.Status.DEGRADED);
        }
    }

    private void recordSuccess(Monitor monitor, CheckOutcome outcome) {
        monitor.setConsecutiveFailures(0);
        int successes = monitor.getConsecutiveSuccesses() + 1;
        monitor.setConsecutiveSuccesses(successes);
        boolean slow = outcome.responseTimeMs() != null && outcome.responseTimeMs() > monitor.getResponseTimeThresholdMs();
        if (monitor.getStatus() == Monitor.Status.DOWN && successes < monitor.getRecoveryThreshold()) return;
        monitor.setStatus(slow ? Monitor.Status.DEGRADED : Monitor.Status.UP);
        if (successes >= monitor.getRecoveryThreshold()) {
            incidents.findFirstByMonitorIdAndStateInOrderByStartedAtDesc(monitor.getId(), List.of("OPEN", "ACKNOWLEDGED"))
                    .ifPresent(incident -> {
                        incident.resolve(outcome.completedAt());
                        if (monitor.getOwner().isRecoveryEmailsEnabled()) {
                            events.publishEvent(new IncidentRecoveredEvent(incident.getId(), monitor.getOwner().getEmail(),
                                    monitor.getName(), incident.getStartedAt(), outcome.completedAt()));
                        }
                    });
        }
    }

    private String host(String url) {
        try { return java.net.URI.create(url).getHost(); }
        catch (IllegalArgumentException exception) { return "configured endpoint"; }
    }
}