package dev.apisentinel.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "time_zone", nullable = false, length = 64) private String timeZone = "UTC";
    @Column(name = "incident_emails_enabled", nullable = false) private boolean incidentEmailsEnabled = true;
    @Column(name = "recovery_emails_enabled", nullable = false) private boolean recoveryEmailsEnabled = true;

    protected AppUser() {}
    public AppUser(String name, String email) {
        this.id = UUID.randomUUID(); this.name = name; this.email = email;
    }
    @PrePersist void beforeInsert() { if (createdAt == null) createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTimeZone() { return timeZone; }
    public boolean isIncidentEmailsEnabled() { return incidentEmailsEnabled; }
    public boolean isRecoveryEmailsEnabled() { return recoveryEmailsEnabled; }
    public void setName(String value) { name = value; }
    public void setTimeZone(String value) { timeZone = ZoneId.of(value).getId(); }
    public void setIncidentEmailsEnabled(boolean value) { incidentEmailsEnabled = value; }
    public void setRecoveryEmailsEnabled(boolean value) { recoveryEmailsEnabled = value; }
}