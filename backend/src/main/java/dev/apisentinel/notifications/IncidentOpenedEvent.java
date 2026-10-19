package dev.apisentinel.notifications;

import java.time.Instant;
import java.util.UUID;

public record IncidentOpenedEvent(UUID incidentId, String recipient, String monitorName,
        String host, String summary, Instant startedAt) {}