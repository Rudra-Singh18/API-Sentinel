package dev.apisentinel.monitors;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitorRepository extends JpaRepository<Monitor, UUID> {
    List<Monitor> findByOwnerIdAndArchivedFalseOrderByCreatedAtDesc(UUID ownerId);
    Optional<Monitor> findByIdAndOwnerIdAndArchivedFalse(UUID id, UUID ownerId);
    long countByOwnerIdAndArchivedFalse(UUID ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Monitor m where m.enabled = true and m.archived = false and m.nextCheckAt <= :now order by m.nextCheckAt")
    List<Monitor> lockDue(@Param("now") Instant now, Pageable pageable);
}