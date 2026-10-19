package dev.apisentinel.statuspages;

import dev.apisentinel.checks.CheckResultRepository;
import dev.apisentinel.incidents.IncidentRepository;
import dev.apisentinel.monitors.Monitor;
import dev.apisentinel.monitors.MonitorRepository;
import dev.apisentinel.statuspages.StatusPageDtos.PublicIncident;
import dev.apisentinel.statuspages.StatusPageDtos.PublicService;
import dev.apisentinel.statuspages.StatusPageDtos.PublicStatusResponse;
import dev.apisentinel.statuspages.StatusPageDtos.SaveStatusPageRequest;
import dev.apisentinel.statuspages.StatusPageDtos.StatusPageResponse;
import dev.apisentinel.users.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StatusPageService {
    private final StatusPageRepository pages;
    private final MonitorRepository monitors;
    private final CheckResultRepository checks;
    private final IncidentRepository incidents;
    private final UserRepository users;

    public StatusPageService(StatusPageRepository pages, MonitorRepository monitors, CheckResultRepository checks,
            IncidentRepository incidents, UserRepository users) {
        this.pages = pages; this.monitors = monitors; this.checks = checks; this.incidents = incidents; this.users = users;
    }

    @Transactional(readOnly = true)
    public List<StatusPageResponse> list(UUID ownerId) { return pages.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(StatusPageDtos.StatusPageResponse::from).toList(); }

    @Transactional
    public StatusPageResponse create(UUID ownerId, SaveStatusPageRequest input) {
        if (pages.existsBySlug(input.slug())) throw new ResponseStatusException(HttpStatus.CONFLICT, "That status-page URL is already in use.");
        var owner = users.findById(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        StatusPage page = pages.save(new StatusPage(owner, input.title().trim(), input.slug(), clean(input.description()), ownedMonitors(ownerId, input.monitorIds())));
        return StatusPageDtos.StatusPageResponse.from(page);
    }

    @Transactional
    public StatusPageResponse update(UUID ownerId, UUID pageId, SaveStatusPageRequest input) {
        StatusPage page = ownedPage(ownerId, pageId);
        if (!page.getSlug().equals(input.slug()) && pages.existsBySlug(input.slug())) throw new ResponseStatusException(HttpStatus.CONFLICT, "That status-page URL is already in use.");
        page.update(input.title().trim(), input.slug(), clean(input.description()), ownedMonitors(ownerId, input.monitorIds()));
        return StatusPageDtos.StatusPageResponse.from(page);
    }

    @Transactional
    public StatusPageResponse publish(UUID ownerId, UUID pageId, boolean published) {
        StatusPage page = ownedPage(ownerId, pageId);
        page.setPublished(published);
        return StatusPageDtos.StatusPageResponse.from(page);
    }

    @Transactional
    public void delete(UUID ownerId, UUID pageId) { pages.delete(ownedPage(ownerId, pageId)); }

    @Transactional(readOnly = true)
    public PublicStatusResponse publicPage(String slug) {
        StatusPage page = pages.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published status page not found."));
        List<Monitor> services = page.getMonitors().stream().filter(monitor -> !monitor.isArchived()).toList();
        Instant since = Instant.now().minus(Duration.ofHours(24));
        List<PublicService> publicServices = services.stream().map(monitor -> {
            List<dev.apisentinel.checks.CheckResult> latest = checks.findByMonitorIdOrderByCompletedAtDesc(monitor.getId(), PageRequest.of(0, 1));
            long total = checks.countByMonitorIdAndCompletedAtAfter(monitor.getId(), since);
            long available = checks.countByMonitorIdAndOutcomeAndCompletedAtAfter(monitor.getId(), "AVAILABLE", since);
            Double uptime = total == 0 ? null : available * 100.0 / total;
            return new PublicService(monitor.getName(), monitor.getStatus(), uptime, latest.isEmpty() ? null : latest.get(0).getCompletedAt());
        }).toList();
        List<UUID> monitorIds = services.stream().map(Monitor::getId).toList();
        List<PublicIncident> recent = monitorIds.isEmpty() ? List.of() : incidents.findByMonitorIdInOrderByStartedAtDesc(monitorIds, PageRequest.of(0, 10))
                .stream().map(incident -> new PublicIncident(incident.getMonitor().getName(), incident.getState(), incident.getStartedAt(), incident.getResolvedAt())).toList();
        String overall = overallStatus(services);
        return new PublicStatusResponse(page.getTitle(), page.getDescription(), overall, Instant.now(), publicServices, recent);
    }

    private StatusPage ownedPage(UUID ownerId, UUID pageId) {
        return pages.findByIdAndOwnerId(pageId, ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Status page not found."));
    }

    private List<Monitor> ownedMonitors(UUID ownerId, List<UUID> requestedIds) {
        Set<UUID> distinctIds = new HashSet<>(requestedIds);
        if (distinctIds.size() != requestedIds.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A monitor can only be selected once.");
        List<Monitor> owned = monitors.findByOwnerIdAndArchivedFalseOrderByCreatedAtDesc(ownerId).stream()
                .filter(monitor -> distinctIds.contains(monitor.getId())).toList();
        if (owned.size() != distinctIds.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All selected monitors must belong to this account.");
        return owned;
    }

    private String clean(String description) { return description == null ? "" : description.trim(); }

    private String overallStatus(List<Monitor> services) {
        if (services.isEmpty()) return "PENDING";
        if (services.stream().anyMatch(monitor -> monitor.getStatus() == Monitor.Status.DOWN)) return "DOWN";
        if (services.stream().anyMatch(monitor -> monitor.getStatus() == Monitor.Status.DEGRADED)) return "DEGRADED";
        if (services.stream().anyMatch(monitor -> monitor.getStatus() == Monitor.Status.PENDING || monitor.getStatus() == Monitor.Status.PAUSED)) return "PENDING";
        return "UP";
    }
}