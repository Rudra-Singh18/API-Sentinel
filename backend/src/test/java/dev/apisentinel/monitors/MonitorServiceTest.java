package dev.apisentinel.monitors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import dev.apisentinel.checks.CheckResultRepository;
import dev.apisentinel.security.UrlSafetyPolicy;
import dev.apisentinel.users.AppUser;
import dev.apisentinel.users.UserRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {
    @Mock private MonitorRepository monitors;
    @Mock private CheckResultRepository checks;
    @Mock private UserRepository users;
    @Mock private UrlSafetyPolicy urlPolicy;

    @Test
    void updateChangesConfigurationAndResetsStatusForAnImmediateCheck() {
        UUID ownerId = UUID.randomUUID();
        AppUser owner = new AppUser("Avery", "avery@example.test");
        Monitor monitor = new Monitor(owner, "Payments", "https://old.example.test/health", "GET", 200, 60, 5000, 2, 2);
        monitor.setStatus(Monitor.Status.DOWN);
        monitor.setConsecutiveFailures(3);
        when(monitors.findByIdAndOwnerIdAndArchivedFalse(monitor.getId(), ownerId)).thenReturn(Optional.of(monitor));
        when(urlPolicy.validate("https://new.example.test/ready")).thenReturn(URI.create("https://new.example.test/ready"));
        when(checks.findByMonitorIdOrderByCompletedAtDesc(monitor.getId(), PageRequest.of(0, 1))).thenReturn(List.of());
        when(checks.countByMonitorIdAndCompletedAtAfter(org.mockito.ArgumentMatchers.eq(monitor.getId()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);
        when(checks.countByMonitorIdAndOutcomeAndCompletedAtAfter(org.mockito.ArgumentMatchers.eq(monitor.getId()),
                org.mockito.ArgumentMatchers.eq("AVAILABLE"), org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        MonitorDtos.MonitorResponse response = new MonitorService(monitors, checks, users, urlPolicy, 10).update(ownerId,
                monitor.getId(), new MonitorDtos.CreateMonitorRequest("Payments ready", "https://new.example.test/ready",
                        "HEAD", 204, 300, 3000, 3, 4, 2000));

        assertEquals("Payments ready", response.name());
        assertEquals("HEAD", response.method());
        assertEquals(204, response.expectedStatus());
        assertEquals(300, response.intervalSeconds());
        assertEquals(3000, response.timeoutMs());
        assertEquals(3, response.failureThreshold());
        assertEquals(4, response.recoveryThreshold());
        assertEquals(2000, response.responseTimeThresholdMs());
        assertEquals(Monitor.Status.PENDING, response.status());
        assertEquals(0, monitor.getConsecutiveFailures());
        assertEquals(0, monitor.getConsecutiveSuccesses());
        assertTrue(monitor.getNextCheckAt().isAfter(Instant.now().minusSeconds(1)));
    }
}
