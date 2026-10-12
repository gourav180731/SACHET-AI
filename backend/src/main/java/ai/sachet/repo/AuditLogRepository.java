package ai.sachet.repo;

import ai.sachet.domain.AuditLogEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
  Optional<AuditLogEntry> findTopByOrderByIdDesc();
  List<AuditLogEntry> findAllByOrderByIdAsc();
  Page<AuditLogEntry> findAllByOrderByIdDesc(Pageable pageable);
}
