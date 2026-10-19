package dev.apisentinel.checks;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckResultRepository extends JpaRepository<CheckResult, UUID> {
    List<CheckResult> findByMonitorIdAndCompletedAtAfterOrderByCompletedAtAsc(UUID monitorId, Instant since);
    List<CheckResult> findByMonitorIdOrderByCompletedAtDesc(UUID monitorId, Pageable pageable);
    long countByMonitorIdAndCompletedAtAfter(UUID monitorId, Instant since);
    long countByMonitorIdAndOutcomeAndCompletedAtAfter(UUID monitorId, String outcome, Instant since);

    @Query("select c from CheckResult c where c.monitor.owner.id = :ownerId and c.completedAt >= :since order by c.completedAt desc")
    List<CheckResult> findRecentForOwner(@Param("ownerId") UUID ownerId, @Param("since") Instant since, Pageable pageable);
}