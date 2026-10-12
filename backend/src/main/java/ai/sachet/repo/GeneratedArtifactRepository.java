package ai.sachet.repo;

import ai.sachet.domain.GeneratedArtifact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedArtifactRepository extends JpaRepository<GeneratedArtifact, String> {
  Page<GeneratedArtifact> findByOwnerIdOrderByCreatedAtDesc(String ownerId, Pageable pageable);
  long countByOwnerId(String ownerId);
}
