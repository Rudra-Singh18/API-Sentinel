package dev.apisentinel.incidents;

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

@Entity @Table(name = "incidents")
public class Incident {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "monitor_id", nullable = false) private Monitor monitor;
    @Column(nullable = false, length = 16) private String state;
    @Column(nullable = false, length = 240) private String summary;
    @Column(name = "failure_category", length = 32) private String failureCategory;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "acknowledged_at") private Instant acknowledgedAt;
    protected Incident() {}
    public Incident(Monitor monitor, String summary, String category, Instant startedAt) {
        this.id = UUID.randomUUID(); this.monitor = monitor; this.state = "OPEN"; this.summary = summary;
        this.failureCategory = category; this.startedAt = startedAt;
    }
    public UUID getId() { return id; }
    public Monitor getMonitor() { return monitor; }
    public String getState() { return state; }
    public String getSummary() { return summary; }
    public String getFailureCategory() { return failureCategory; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void acknowledge(Instant at) { if ("OPEN".equals(state)) { state = "ACKNOWLEDGED"; acknowledgedAt = at; } }
    public void resolve(Instant at) { state = "RESOLVED"; resolvedAt = at; }
    public void updateFailure(String text, String category) { summary = text; failureCategory = category; }
}