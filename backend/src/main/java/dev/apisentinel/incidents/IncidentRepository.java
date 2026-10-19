package dev.apisentinel.incidents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByMonitorOwnerIdOrderByStartedAtDesc(UUID ownerId);
    Optional<Incident> findFirstByMonitorIdAndStateInOrderByStartedAtDesc(UUID monitorId, List<String> states);
    Optional<Incident> findByIdAndMonitorOwnerId(UUID id, UUID ownerId);
    List<Incident> findByMonitorIdInOrderByStartedAtDesc(List<UUID> monitorIds, Pageable pageable);
}