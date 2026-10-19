package dev.apisentinel.notifications;

import java.time.Instant;
import java.util.UUID;

public record IncidentRecoveredEvent(UUID incidentId, String recipient, String monitorName,
        Instant startedAt, Instant recoveredAt) {}