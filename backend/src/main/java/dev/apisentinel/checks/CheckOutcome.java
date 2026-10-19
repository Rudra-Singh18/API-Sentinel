package dev.apisentinel.checks;

import java.time.Instant;

public record CheckOutcome(Instant startedAt, Instant completedAt, boolean available, Integer httpStatus,
        Long responseTimeMs, String failureCategory, String diagnosticMessage) {}