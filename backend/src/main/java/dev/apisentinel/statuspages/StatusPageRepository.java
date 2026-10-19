package dev.apisentinel.statuspages;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusPageRepository extends JpaRepository<StatusPage, UUID> {
    List<StatusPage> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Optional<StatusPage> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<StatusPage> findBySlugAndPublishedTrue(String slug);
    boolean existsBySlug(String slug);
}