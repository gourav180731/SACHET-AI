package ai.sachet.rag;

import ai.sachet.repo.DocumentRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;

/** Shared authorization scoping: readable CURRENT document IDs for a caller. */
final class RetrievalScope {

  private RetrievalScope() {
  }

  static List<String> readableIds(DocumentRepository documents,
      VectorStore.VectorSearchFilter filter, int cap) {
    List<String> roles = filter.roles() == null || filter.roles().isEmpty()
        ? List.of("") : filter.roles();
    List<String> ids = documents.findReadableIds(filter.userId(), roles,
        filter.department(), filter.bands(), PageRequest.ofSize(cap));
    if (filter.docIds() != null && !filter.docIds().isEmpty()) {
      ids = ids.stream().filter(filter.docIds()::contains).toList();
    }
    return ids;
  }
}
