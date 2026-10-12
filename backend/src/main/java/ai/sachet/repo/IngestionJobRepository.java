package ai.sachet.repo;

import ai.sachet.domain.IngestionJob;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, String> {
  List<IngestionJob> findByDocumentIdOrderByCreatedAtDesc(String documentId);
  Optional<IngestionJob> findTopByDocumentIdOrderByCreatedAtDesc(String documentId);
  void deleteByDocumentId(String documentId);
}
