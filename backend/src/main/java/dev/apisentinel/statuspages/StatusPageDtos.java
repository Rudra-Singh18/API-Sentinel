package dev.apisentinel.statuspages;

import dev.apisentinel.monitors.Monitor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StatusPageDtos {
    private StatusPageDtos() {}

    public record SaveStatusPageRequest(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
            @Size(max = 500) String description,
            @NotNull @Size(max = 10) List<UUID> monitorIds) {}

    public record MonitorSummary(UUID id, String name, Monitor.Status status) {}
    public record StatusPageResponse(UUID id, String title, String slug, String description, boolean published,
            Instant updatedAt, List<MonitorSummary> monitors) {
        static StatusPageResponse from(StatusPage page) {
            return new StatusPageResponse(page.getId(), page.getTitle(), page.getSlug(), page.getDescription(), page.isPublished(),
                    page.getUpdatedAt(), page.getMonitors().stream().filter(monitor -> !monitor.isArchived())
                    .map(monitor -> new MonitorSummary(monitor.getId(), monitor.getName(), monitor.getStatus())).toList());
        }
    }

    public record PublicService(String name, Monitor.Status status, Double uptimePercent, Instant lastCheckedAt) {}
    public record PublicIncident(String monitorName, String state, Instant startedAt, Instant resolvedAt) {}
    public record PublicStatusResponse(String title, String description, String overallStatus, Instant generatedAt,
            List<PublicService> services, List<PublicIncident> incidents) {}
}