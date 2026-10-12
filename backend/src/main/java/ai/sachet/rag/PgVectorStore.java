package ai.sachet.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * EXPERIMENTAL native pgvector store (HNSW + pre-filter in one query).
 * NOT the default and NOT runtime-verified in this environment (no pgvector
 * host available): enable only after applying the operator DDL in
 * docs/RAG_ARCHITECTURE.md. The permission pre-filter mirrors the tested JVM
 * path predicate-for-predicate; {@link #sqlFor} is unit-tested for the
 * presence of every authorization predicate.
 *
 * Operator DDL (PostgreSQL + pgvector extension):
 * <pre>
 * CREATE EXTENSION IF NOT EXISTS vector;
 * ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS embedding_vec vector(384);
 * CREATE INDEX IF NOT EXISTS idx_chunks_vec ON document_chunks
 *   USING hnsw (embedding_vec vector_cosine_ops);
 * -- populated from the portable embedding column, e.g. by a migration job.
 * </pre>
 */
@Service
@ConditionalOnProperty(name = "sachet.vector-store", havingValue = "pgvector")
public class PgVectorStore implements VectorStore {

  private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);

  private final DataSource dataSource;
  private volatile JdbcTemplate jdbc;
  private volatile Boolean ready;

  public PgVectorStore(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Lazily built (constructor must stay usable with an absent datasource in unit tests). */
  private JdbcTemplate jdbc() {
    if (jdbc == null) {
      synchronized (this) {
        if (jdbc == null) {
          if (dataSource == null) {
            throw new IllegalStateException("no datasource");
          }
          jdbc = new JdbcTemplate(dataSource);
        }
      }
    }
    return jdbc;
  }

  /** True when the extension + vector column exist. Cached after first probe. */
  public boolean available() {
    if (ready != null) {
      return ready;
    }
    synchronized (this) {
      if (ready != null) {
        return ready;
      }
      ready = probe();
      if (!ready) {
        log.warn("pgvector unavailable (extension or embedding_vec missing);"
            + " retrieval falls back to the JVM store");
      }
      return ready;
    }
  }

  private boolean probe() {
    try {
      Integer ext = jdbc.queryForObject(
          "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
      if (ext == null || ext == 0) {
        return false;
      }
      Integer col = jdbc.queryForObject(
          "SELECT COUNT(*) FROM information_schema.columns"
              + " WHERE table_name = 'document_chunks' AND column_name = 'embedding_vec'",
          Integer.class);
      return col != null && col > 0;
    } catch (Exception e) {
      log.warn("pgvector probe failed: {}", e.getClass().getSimpleName());
      return false;
    }
  }

  @Override
  public List<ScoredChunk> search(float[] query, VectorSearchFilter filter, int topK) {
    if (!available()) {
      throw new EmbeddingUnavailableException("pgvector store unavailable.");
    }
    String qvec = "[" + FloatArrayCodec.encode(query) + "]";
    List<Object> args = new ArrayList<>();
    String sql = sqlFor(filter, topK, args);
    args.add(0, qvec);
    return jdbc.query(sql, args.toArray(), (rs, i) ->
        new ScoredChunk(rs.getLong("id"), 1.0 - rs.getDouble("dist")));
  }

  /**
   * Builds the pre-filtered HNSW query. Authorization predicates (owner,
   * USER/ROLE/DEPARTMENT grants, classification bands, CURRENT status) are
   * structural — the caller supplies only values, and {@code :qvec} ordering
   * never widens the candidate set.
   */
  /** Visible for the SQL-review unit test (authorization predicates must be present). */
  public String sqlFor(VectorSearchFilter filter, int topK, List<Object> args) {
    StringBuilder sql = new StringBuilder(
        "SELECT c.id, (c.embedding_vec <=> CAST(? AS vector)) AS dist"
            + " FROM document_chunks c JOIN documents d ON d.id = c.document_id"
            + " WHERE d.status = 'CURRENT' AND c.embedding_vec IS NOT NULL");
    if (filter.bands() == null || filter.bands().isEmpty()) {
      sql.append(" AND 1 = 0");
    } else {
      sql.append(" AND d.classification IN (")
          .append(filter.bands().stream().map(b -> "?").collect(Collectors.joining(",")))
          .append(")");
      args.addAll(filter.bands());
    }
    sql.append(" AND (d.owner_id = ?");
    args.add(filter.userId());
    sql.append(" OR EXISTS (SELECT 1 FROM document_permissions g WHERE g.document_id = d.id"
        + " AND g.can_read AND ((g.principal_type = 'USER' AND g.principal_id = ?)");
    args.add(filter.userId());
    List<String> roles = filter.roles() == null || filter.roles().isEmpty()
        ? List.of("") : filter.roles();
    sql.append(" OR (g.principal_type = 'ROLE' AND g.principal_id IN (")
        .append(roles.stream().map(r -> "?").collect(Collectors.joining(",")))
        .append("))");
    args.addAll(roles);
    sql.append(" OR (g.principal_type = 'DEPARTMENT' AND g.principal_id = ?)");
    args.add(filter.department());
    sql.append(")))");
    if (filter.docIds() != null && !filter.docIds().isEmpty()) {
      sql.append(" AND d.id IN (")
          .append(filter.docIds().stream().map(d -> "?").collect(Collectors.joining(",")))
          .append(")");
      args.addAll(filter.docIds());
    }
    sql.append(String.format(Locale.ROOT, " ORDER BY dist ASC LIMIT %d", Math.max(topK, 1)));
    return sql.toString();
  }
}
