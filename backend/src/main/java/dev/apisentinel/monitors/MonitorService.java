package dev.apisentinel.monitors;

import dev.apisentinel.checks.CheckResult;
import dev.apisentinel.checks.CheckResultRepository;
import dev.apisentinel.monitors.MonitorDtos.CreateMonitorRequest;
import dev.apisentinel.monitors.MonitorDtos.MonitorResponse;
import dev.apisentinel.security.UrlSafetyPolicy;
import dev.apisentinel.users.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonitorService {
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);
    private final MonitorRepository monitors;
    private final CheckResultRepository checks;
    private final UserRepository users;
    private final UrlSafetyPolicy urlPolicy;
    private final int monitorLimit;

    public MonitorService(MonitorRepository monitors, CheckResultRepository checks, UserRepository users,
            UrlSafetyPolicy urlPolicy, @Value("${sentinel.max-monitors-per-user:10}") int monitorLimit) {
        this.monitors = monitors; this.checks = checks; this.users = users; this.urlPolicy = urlPolicy; this.monitorLimit = monitorLimit;
    }

    @Transactional(readOnly = true)
    public List<MonitorResponse> list(UUID ownerId) {
        return monitors.findByOwnerIdAndArchivedFalseOrderByCreatedAtDesc(ownerId).stream().map(this::view).toList();
    }

    @Transactional
    public MonitorResponse create(UUID ownerId, CreateMonitorRequest input) {
        if (!List.of(60, 300, 900).contains(input.intervalSeconds())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check interval must be 60, 300, or 900 seconds.");
        if (monitors.countByOwnerIdAndArchivedFalse(ownerId) >= monitorLimit) throw new ResponseStatusException(HttpStatus.CONFLICT, "Monitor limit reached for this account.");
        String url = urlPolicy.validate(input.url()).toASCIIString();
        var owner = users.findById(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Monitor monitor = new Monitor(owner, input.name().trim(), url, input.method(), input.expectedStatus(),
                input.intervalSeconds(), input.timeoutMs(), input.failureThreshold(), input.recoveryThreshold());
        if (input.responseTimeThresholdMs() != null) monitor.setResponseTimeThresholdMs(input.responseTimeThresholdMs());
        monitor = monitors.save(monitor);
        return view(monitor);
    }

    @Transactional
    public MonitorResponse update(UUID ownerId, UUID id, CreateMonitorRequest input) {
        if (!List.of(60, 300, 900).contains(input.intervalSeconds())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check interval must be 60, 300, or 900 seconds.");
        String url = urlPolicy.validate(input.url()).toASCIIString();
        Monitor monitor = getOwned(ownerId, id);
        monitor.setName(input.name().trim());
        monitor.setUrl(url);
        monitor.setMethod(input.method());
        monitor.setExpectedStatus(input.expectedStatus());
        monitor.setIntervalSeconds(input.intervalSeconds());
        monitor.setTimeoutMs(input.timeoutMs());
        monitor.setFailureThreshold(input.failureThreshold());
        monitor.setRecoveryThreshold(input.recoveryThreshold());
        if (input.responseTimeThresholdMs() != null) monitor.setResponseTimeThresholdMs(input.responseTimeThresholdMs());
        monitor.resetAfterConfigurationChange();
        return view(monitor);
    }

    @Transactional(readOnly = true)
    public Monitor getOwned(UUID ownerId, UUID monitorId) {
        return monitors.findByIdAndOwnerIdAndArchivedFalse(monitorId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found."));
    }

    @Transactional
    public void setEnabled(UUID ownerId, UUID id, boolean enabled) {
        Monitor monitor = getOwned(ownerId, id);
        monitor.setEnabled(enabled);
        if (enabled) monitor.setNextCheckAt(Instant.now());
    }

    @Transactional
    public void archive(UUID ownerId, UUID id) {
        Monitor monitor = getOwned(ownerId, id);
        monitor.setEnabled(false);
        monitor.setArchived(true);
    }

    @Transactional(readOnly = true)
    public List<CheckResult> checks(UUID ownerId, UUID id, int page, int size) {
        getOwned(ownerId, id);
        return checks.findByMonitorIdOrderByCompletedAtDesc(id, PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional(readOnly = true)
    public List<MonitorDtos.PerformancePoint> performance(UUID ownerId) {
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofHours(24));
        List<CheckResult> recent = checks.findRecentForOwner(ownerId, since, PageRequest.of(0, 5000));
        List<MonitorDtos.PerformancePoint> points = new ArrayList<>(24);
        for (int hour = 23; hour >= 0; hour--) {
            Instant start = now.minus(Duration.ofHours(hour + 1L));
            Instant end = now.minus(Duration.ofHours(hour));
            List<Long> samples = recent.stream().filter(check -> !check.getCompletedAt().isBefore(start) && check.getCompletedAt().isBefore(end))
                    .map(CheckResult::getResponseTimeMs).filter(value -> value != null).toList();
            Long average = samples.isEmpty() ? null : Math.round(samples.stream().mapToLong(Long::longValue).average().orElse(0));
            points.add(new MonitorDtos.PerformancePoint(HOUR_LABEL.format(start), average));
        }
        return points;
    }

    private MonitorResponse view(Monitor monitor) {
        List<CheckResult> latest = checks.findByMonitorIdOrderByCompletedAtDesc(monitor.getId(), PageRequest.of(0, 1));
        Instant since = Instant.now().minus(Duration.ofHours(24));
        long total = checks.countByMonitorIdAndCompletedAtAfter(monitor.getId(), since);
        long available = checks.countByMonitorIdAndOutcomeAndCompletedAtAfter(monitor.getId(), "AVAILABLE", since);
        Double uptime = total == 0 ? null : available * 100.0 / total;
        CheckResult result = latest.isEmpty() ? null : latest.get(0);
        return new MonitorResponse(monitor.getId(), monitor.getName(), monitor.getUrl(), monitor.getMethod(), monitor.getExpectedStatus(),
                monitor.getIntervalSeconds(), monitor.getTimeoutMs(), monitor.getFailureThreshold(), monitor.getRecoveryThreshold(),
                monitor.getResponseTimeThresholdMs(), monitor.isEnabled(), monitor.getStatus(),
                result == null ? null : result.getResponseTimeMs(), result == null ? null : result.getCompletedAt(), uptime);
    }
}