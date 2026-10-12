package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sachet.rag.PgVectorStore;
import ai.sachet.rag.VectorStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * pgvector SQL review as a unit test: every authorization predicate must be
 * present in the generated query (owner, USER/ROLE/DEPARTMENT grants,
 * classification bands, CURRENT status, optional doc scoping, row cap).
 * The store itself stays EXPERIMENTAL until run against a real pgvector host.
 */
class PgVectorSqlTest {

  private PgVectorStore store() {
    return new PgVectorStore(null);
  }

  private VectorStore.VectorSearchFilter filter() {
    return new VectorStore.VectorSearchFilter("u1", List.of("ANALYST"), "C-DOT",
        List.of("PUBLIC", "INTERNAL"), List.of("d1"), List.of("emergency"));
  }

  @Test
  void preFilterPredicatesPresent() {
    List<Object> args = new ArrayList<>();
    String sql = store().sqlFor(filter(), 10, args).toLowerCase();
    assertTrue(sql.contains("d.status = 'current'"), "status gate");
    assertTrue(sql.contains("d.owner_id = ?"), "owner gate");
    assertTrue(sql.contains("document_permissions"), "grant join");
    assertTrue(sql.contains("principal_type = 'user'"), "user grants");
    assertTrue(sql.contains("principal_type = 'role'"), "role grants");
    assertTrue(sql.contains("principal_type = 'department'"), "department grants");
    assertTrue(sql.contains("d.classification in ("), "classification bands");
    assertTrue(sql.contains("order by dist asc limit 10"), "HNSW order + cap");
    assertTrue(sql.contains("c.embedding_vec <=>"), "cosine operator");
    assertTrue(args.contains("u1") && args.contains("d1") && args.contains("ANALYST"));
  }

  @Test
  void emptyBandsMatchNothing() {
    var f = new VectorStore.VectorSearchFilter("u1", List.of(), null,
        List.of(), null, List.of());
    List<Object> args = new ArrayList<>();
    String sql = store().sqlFor(f, 10, args).toLowerCase();
    assertTrue(sql.contains("1 = 0"), "empty clearance must match nothing");
  }

  @Test
  void unavailableWithoutDatasourceProbe() {
    // Null datasource: probe fails safely (false), never throws.
    assertFalse(store().available());
  }
}
