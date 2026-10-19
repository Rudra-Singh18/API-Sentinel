package dev.apisentinel.statuspages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.apisentinel.monitors.Monitor;
import dev.apisentinel.monitors.MonitorRepository;
import dev.apisentinel.statuspages.StatusPageDtos.SaveStatusPageRequest;
import dev.apisentinel.users.AppUser;
import dev.apisentinel.users.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class StatusPageIntegrationTest {
    @Autowired private StatusPageService service;
    @Autowired private UserRepository users;
    @Autowired private MonitorRepository monitors;

    @Test
    void publicResponseContainsStatusButNeverTargetUrlOrDiagnostics() {
        AppUser owner = users.save(new AppUser("Status Owner", "status-owner@example.test"));
        Monitor monitor = monitors.save(new Monitor(owner, "Payments", "https://internal-looking.example.test/private-path", "GET", 200, 60, 5000, 2, 2));
        var page = service.create(owner.getId(), new SaveStatusPageRequest("Payments", "payments-public-" + UUID.randomUUID().toString().substring(0, 8), "Services for customers", List.of(monitor.getId())));

        assertThrows(ResponseStatusException.class, () -> service.publicPage(page.slug()));
        service.publish(owner.getId(), page.id(), true);
        var result = service.publicPage(page.slug());

        assertEquals("Payments", result.title());
        assertEquals("PENDING", result.overallStatus());
        assertEquals(1, result.services().size());
        assertEquals("Payments", result.services().get(0).name());
        assertEquals(null, result.services().get(0).uptimePercent());
        assertFalse(result.toString().contains("internal-looking.example.test"));
        assertFalse(result.toString().contains("private-path"));
    }

    @Test
    void rejectsMonitorsThatBelongToAnotherAccount() {
        AppUser owner = users.save(new AppUser("Page Owner", "page-owner@example.test"));
        AppUser other = users.save(new AppUser("Other Owner", "other-owner@example.test"));
        Monitor foreignMonitor = monitors.save(new Monitor(other, "Private service", "https://example.test/health", "GET", 200, 60, 5000, 2, 2));

        assertThrows(ResponseStatusException.class, () -> service.create(owner.getId(),
                new SaveStatusPageRequest("Page", "private-page-" + UUID.randomUUID().toString().substring(0, 8), "", List.of(foreignMonitor.getId()))));
    }
}