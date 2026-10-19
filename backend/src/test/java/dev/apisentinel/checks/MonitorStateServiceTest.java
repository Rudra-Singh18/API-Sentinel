package dev.apisentinel.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.apisentinel.incidents.Incident;
import dev.apisentinel.incidents.IncidentRepository;
import dev.apisentinel.monitors.Monitor;
import dev.apisentinel.monitors.MonitorRepository;
import dev.apisentinel.users.AppUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MonitorStateServiceTest {
    @Mock private MonitorRepository monitors;
    @Mock private CheckResultRepository checks;
    @Mock private IncidentRepository incidents;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private MonitorStateService service;

    @Test
    void opensOnlyOneIncidentAfterConsecutiveFailureThreshold() {
        Monitor monitor = monitor();
        when(monitors.findById(monitor.getId())).thenReturn(Optional.of(monitor));
        AtomicReference<Incident> active = new AtomicReference<>();
        when(incidents.findFirstByMonitorIdAndStateInOrderByStartedAtDesc(eq(monitor.getId()), anyList()))
                .thenAnswer(invocation -> Optional.ofNullable(active.get()));
        when(incidents.save(any())).thenAnswer(invocation -> {
            active.set(invocation.getArgument(0));
            return active.get();
        });

        service.record(monitor.getId(), failedAt(Instant.now()));
        assertEquals(Monitor.Status.DEGRADED, monitor.getStatus());
        service.record(monitor.getId(), failedAt(Instant.now()));
        service.record(monitor.getId(), failedAt(Instant.now()));

        assertEquals(Monitor.Status.DOWN, monitor.getStatus());
        verify(incidents, times(1)).save(any(Incident.class));
        verify(checks, times(3)).save(any(CheckResult.class));
    }

    @Test
    void resolvesOnlyAfterRecoveryThreshold() {
        Monitor monitor = monitor();
        monitor.setStatus(Monitor.Status.DOWN);
        monitor.setConsecutiveFailures(2);
        Incident incident = new Incident(monitor, "Connection timed out", "TIMEOUT", Instant.now().minusSeconds(30));
        when(monitors.findById(monitor.getId())).thenReturn(Optional.of(monitor));
        when(incidents.findFirstByMonitorIdAndStateInOrderByStartedAtDesc(eq(monitor.getId()), anyList()))
                .thenReturn(Optional.of(incident));

        service.record(monitor.getId(), succeededAt(Instant.now()));
        assertEquals(Monitor.Status.DOWN, monitor.getStatus());
        assertEquals("OPEN", incident.getState());
        service.record(monitor.getId(), succeededAt(Instant.now()));

        assertEquals(Monitor.Status.UP, monitor.getStatus());
        assertEquals("RESOLVED", incident.getState());
        assertNotNull(incident.getResolvedAt());
    }

    @Test
    void disabledEmailPreferencesSuppressIncidentAndRecoveryNotifications() {
        Monitor monitor = monitor();
        monitor.getOwner().setIncidentEmailsEnabled(false);
        monitor.getOwner().setRecoveryEmailsEnabled(false);
        when(monitors.findById(monitor.getId())).thenReturn(Optional.of(monitor));
        AtomicReference<Incident> active = new AtomicReference<>();
        when(incidents.findFirstByMonitorIdAndStateInOrderByStartedAtDesc(eq(monitor.getId()), anyList()))
                .thenAnswer(invocation -> Optional.ofNullable(active.get()));
        when(incidents.save(any())).thenAnswer(invocation -> {
            active.set(invocation.getArgument(0));
            return active.get();
        });

        service.record(monitor.getId(), failedAt(Instant.now()));
        service.record(monitor.getId(), failedAt(Instant.now()));
        service.record(monitor.getId(), succeededAt(Instant.now()));
        service.record(monitor.getId(), succeededAt(Instant.now()));

        assertEquals("RESOLVED", active.get().getState());
        verify(events, never()).publishEvent(any(Object.class));
    }

    private Monitor monitor() {
        return new Monitor(new AppUser("Avery", "avery@example.test"), "API", "https://example.test/health", "GET", 200, 60, 5000, 2, 2);
    }

    private CheckOutcome failedAt(Instant started) {
        return new CheckOutcome(started, started.plusMillis(15), false, null, 15L, "TIMEOUT", "Timed out.");
    }

    private CheckOutcome succeededAt(Instant started) {
        return new CheckOutcome(started, started.plusMillis(80), true, 200, 80L, null, null);
    }
}
