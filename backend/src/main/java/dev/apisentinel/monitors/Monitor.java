package dev.apisentinel.monitors;

import dev.apisentinel.users.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monitors")
public class Monitor {
    public enum Status { PENDING, UP, DEGRADED, DOWN, PAUSED }
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false) private AppUser owner;
    @Column(nullable = false, length = 80) private String name;
    @Column(name = "target_url", nullable = false, length = 2048) private String url;
    @Column(name = "http_method", nullable = false, length = 8) private String method;
    @Column(name = "expected_status", nullable = false) private int expectedStatus;
    @Column(name = "interval_seconds", nullable = false) private int intervalSeconds;
    @Column(name = "timeout_ms", nullable = false) private int timeoutMs;
    @Column(name = "failure_threshold", nullable = false) private int failureThreshold;
    @Column(name = "recovery_threshold", nullable = false) private int recoveryThreshold;
    @Column(name = "response_time_threshold_ms", nullable = false) private int responseTimeThresholdMs = 1200;
    @Column(nullable = false) private boolean enabled = true;
    @Column(nullable = false) private boolean archived;
    @Enumerated(EnumType.STRING) @Column(name = "current_status", nullable = false, length = 16) private Status status = Status.PENDING;
    @Column(name = "consecutive_failures", nullable = false) private int consecutiveFailures;
    @Column(name = "consecutive_successes", nullable = false) private int consecutiveSuccesses;
    @Column(name = "next_check_at", nullable = false) private Instant nextCheckAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Monitor() {}
    public Monitor(AppUser owner, String name, String url, String method, int expectedStatus, int intervalSeconds, int timeoutMs, int failureThreshold, int recoveryThreshold) {
        this.id = UUID.randomUUID(); this.owner = owner; this.name = name; this.url = url; this.method = method;
        this.expectedStatus = expectedStatus; this.intervalSeconds = intervalSeconds; this.timeoutMs = timeoutMs;
        this.failureThreshold = failureThreshold; this.recoveryThreshold = recoveryThreshold; this.nextCheckAt = Instant.now();
    }
    @PrePersist void beforeInsert() { if (createdAt == null) createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getMethod() { return method; }
    public int getExpectedStatus() { return expectedStatus; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public int getTimeoutMs() { return timeoutMs; }
    public int getFailureThreshold() { return failureThreshold; }
    public int getRecoveryThreshold() { return recoveryThreshold; }
    public int getResponseTimeThresholdMs() { return responseTimeThresholdMs; }
    public boolean isEnabled() { return enabled; }
    public boolean isArchived() { return archived; }
    public Status getStatus() { return enabled ? status : Status.PAUSED; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
    public Instant getNextCheckAt() { return nextCheckAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setName(String value) { name = value; }
    public void setUrl(String value) { url = value; }
    public void setMethod(String value) { method = value; }
    public void setExpectedStatus(int value) { expectedStatus = value; }
    public void setIntervalSeconds(int value) { intervalSeconds = value; }
    public void setTimeoutMs(int value) { timeoutMs = value; }
    public void setFailureThreshold(int value) { failureThreshold = value; }
    public void setRecoveryThreshold(int value) { recoveryThreshold = value; }
    public void setResponseTimeThresholdMs(int value) { responseTimeThresholdMs = value; }
    public void setEnabled(boolean value) { enabled = value; status = value ? Status.PENDING : Status.PAUSED; }
    public void setArchived(boolean value) { archived = value; }
    public void setStatus(Status value) { status = value; }
    public void setConsecutiveFailures(int value) { consecutiveFailures = value; }
    public void setConsecutiveSuccesses(int value) { consecutiveSuccesses = value; }
    public void setNextCheckAt(Instant value) { nextCheckAt = value; }
    public void resetAfterConfigurationChange() {
        consecutiveFailures = 0;
        consecutiveSuccesses = 0;
        if (enabled) status = Status.PENDING;
        nextCheckAt = Instant.now();
    }
}