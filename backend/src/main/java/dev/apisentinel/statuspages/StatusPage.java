package dev.apisentinel.statuspages;

import dev.apisentinel.monitors.Monitor;
import dev.apisentinel.users.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "status_pages")
public class StatusPage {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false) private AppUser owner;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, unique = true, length = 64) private String slug;
    @Column(nullable = false, length = 500) private String description = "";
    @Column(nullable = false) private boolean published;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "status_page_monitors", joinColumns = @JoinColumn(name = "status_page_id"), inverseJoinColumns = @JoinColumn(name = "monitor_id"))
    private List<Monitor> monitors = new ArrayList<>();

    protected StatusPage() {}
    public StatusPage(AppUser owner, String title, String slug, String description, List<Monitor> monitors) {
        this.id = UUID.randomUUID(); this.owner = owner; this.title = title; this.slug = slug;
        this.description = description; this.monitors = new ArrayList<>(monitors);
    }
    @PrePersist void beforeInsert() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; if (updatedAt == null) updatedAt = now; }
    @PreUpdate void beforeUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public boolean isPublished() { return published; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Monitor> getMonitors() { return monitors; }
    public void update(String title, String slug, String description, List<Monitor> monitors) {
        this.title = title; this.slug = slug; this.description = description; this.monitors = new ArrayList<>(monitors);
    }
    public void setPublished(boolean published) { this.published = published; }
}