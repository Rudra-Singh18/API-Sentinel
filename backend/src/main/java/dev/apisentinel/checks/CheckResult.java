package dev.apisentinel.checks;

import dev.apisentinel.monitors.Monitor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "check_results")
public class CheckResult {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "monitor_id", nullable = false) private Monitor monitor;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;
    @Column(nullable = false, length = 16) private String outcome;
    @Column(name = "http_status") private Integer httpStatus;
    @Column(name = "response_time_ms") private Long responseTimeMs;
    @Column(name = "failure_category", length = 32) private String failureCategory;
    @Column(name = "diagnostic_message", length = 240) private String diagnosticMessage;
    protected CheckResult() {}
    public CheckResult(Monitor monitor, Instant startedAt, Instant completedAt, String outcome, Integer httpStatus, Long responseTimeMs, String failureCategory, String diagnosticMessage) {
        this.id = UUID.randomUUID(); this.monitor = monitor; this.startedAt = startedAt; this.completedAt = completedAt;
        this.outcome = outcome; this.httpStatus = httpStatus; this.responseTimeMs = responseTimeMs;
        this.failureCategory = failureCategory; this.diagnosticMessage = diagnosticMessage;
    }
    public UUID getId() { return id; }
    public Monitor getMonitor() { return monitor; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getOutcome() { return outcome; }
    public Integer getHttpStatus() { return httpStatus; }
    public Long getResponseTimeMs() { return responseTimeMs; }
    public String getFailureCategory() { return failureCategory; }
    public String getDiagnosticMessage() { return diagnosticMessage; }
}