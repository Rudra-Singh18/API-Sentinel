package dev.apisentinel.checks;

import dev.apisentinel.monitors.Monitor;
import dev.apisentinel.monitors.MonitorRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckScheduleService {
    private final MonitorRepository monitors;

    public CheckScheduleService(MonitorRepository monitors) { this.monitors = monitors; }

    @Transactional
    public List<UUID> claimDue(int limit) {
        Instant now = Instant.now();
        List<Monitor> due = monitors.lockDue(now, PageRequest.of(0, limit));
        for (Monitor monitor : due) monitor.setNextCheckAt(now.plusSeconds(monitor.getIntervalSeconds()));
        return due.stream().map(Monitor::getId).toList();
    }

    @Transactional
    public void defer(UUID id) {
        monitors.findById(id).ifPresent(monitor -> monitor.setNextCheckAt(Instant.now()));
    }
}