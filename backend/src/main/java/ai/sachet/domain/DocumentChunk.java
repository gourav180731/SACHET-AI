package ai.sachet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One retrieval unit. Provenance is never dropped: every chunk retains its
 * document, version (via document), page, and section through all later
 * stages (reranking, translation, summarization).
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false, length = 36)
  private String documentId;

  @Column(name = "chunk_index", nullable = false)
  private int chunkIndex;

  @Column(name = "page_number", nullable = false)
  private int pageNumber;

  @Column(length = 512)
  private String section;

  @Column(nullable = false, length = 20000)
  private String text;

  @Column(name = "char_count", nullable = false)
  private int charCount;

  /** L2-normalized embedding (null until the EMBEDDING stage runs). */
  @Column(length = 8000)
  @Convert(converter = ai.sachet.rag.FloatArrayConverter.class)
  private float[] embedding;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected DocumentChunk() {
  }

  public DocumentChunk(String documentId, int chunkIndex, int pageNumber, String section, String text) {
    this.documentId = documentId;
    this.chunkIndex = chunkIndex;
    this.pageNumber = pageNumber;
    this.section = section;
    this.text = text;
    this.charCount = text == null ? 0 : text.length();
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public Long getId() { return id; }
  public String getDocumentId() { return documentId; }
  public int getChunkIndex() { return chunkIndex; }
  public int getPageNumber() { return pageNumber; }
  public String getSection() { return section; }
  public String getText() { return text; }
  public int getCharCount() { return charCount; }
  public float[] getEmbedding() { return embedding; }
  public void setEmbedding(float[] embedding) { this.embedding = embedding; }
}
