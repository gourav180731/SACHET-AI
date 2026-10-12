package ai.sachet.repo;

import ai.sachet.domain.DocumentPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Long> {
  List<DocumentPermission> findByDocumentId(String documentId);
  void deleteByDocumentIdIn(List<String> documentIds);
}
