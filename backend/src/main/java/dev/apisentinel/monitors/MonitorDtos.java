package dev.apisentinel.monitors;

import dev.apisentinel.checks.CheckResult;
import dev.apisentinel.incidents.Incident;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class MonitorDtos {
    private MonitorDtos() {}

    public record CreateMonitorRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 2048) String url,
            @Pattern(regexp = "GET|HEAD") String method,
            @Min(100) @Max(599) int expectedStatus,
            @Min(60) @Max(900) int intervalSeconds,
            @Min(1000) @Max(10000) int timeoutMs,
            @Min(1) @Max(10) int failureThreshold,
            @Min(1) @Max(10) int recoveryThreshold,
            @Min(100) @Max(60000) Integer responseTimeThresholdMs) {}

    public record MonitorResponse(UUID id, String name, String url, String method, int expectedStatus,
            int intervalSeconds, int timeoutMs, int failureThreshold, int recoveryThreshold, int responseTimeThresholdMs,
            boolean enabled, Monitor.Status status,
            Long latestResponseMs, Instant lastCheckedAt, Double uptimePercent) {}

    public record CheckResponse(UUID id, Instant startedAt, Instant completedAt, String outcome,
            Integer httpStatus, Long responseTimeMs, String failureCategory, String diagnosticMessage) {
        public static CheckResponse from(CheckResult check) {
            return new CheckResponse(check.getId(), check.getStartedAt(), check.getCompletedAt(), check.getOutcome(),
                    check.getHttpStatus(), check.getResponseTimeMs(), check.getFailureCategory(), check.getDiagnosticMessage());
        }
    }

    public record IncidentResponse(UUID id, UUID monitorId, String monitorName, String summary,
            String state, String failureCategory, Instant startedAt, Instant resolvedAt) {
        public static IncidentResponse from(Incident incident) {
            return new IncidentResponse(incident.getId(), incident.getMonitor().getId(), incident.getMonitor().getName(),
                    incident.getSummary(), incident.getState(), incident.getFailureCategory(), incident.getStartedAt(), incident.getResolvedAt());
        }
    }

    public record PerformancePoint(String label, Long responseMs) {}
}