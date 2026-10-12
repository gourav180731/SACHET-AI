package ai.sachet.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * Minimal safetensors reader (F32, little-endian, memory-mapped). Reads only
 * what is requested; absolute positional reads are thread-safe.
 */
public final class SafetensorsReader implements AutoCloseable {

  public record TensorInfo(List<Long> shape, long dataStart, long dataEnd) {
  }

  private final ByteBuffer data;
  private final Map<String, TensorInfo> tensors;
  private final FileChannel channel;

  @SuppressWarnings("unchecked")
  public SafetensorsReader(Path file) {
    try {
      this.channel = FileChannel.open(file, StandardOpenOption.READ);
      long fileSize = channel.size();
      ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      readFully(head);
      long headerLen = head.getLong(0);
      if (headerLen <= 0 || headerLen > fileSize) {
        throw new EmbeddingUnavailableException("Invalid safetensors header.");
      }
      ByteBuffer headerBuf = ByteBuffer.allocate((int) headerLen);
      readFully(headerBuf);
      String json = new String(headerBuf.array(), java.nio.charset.StandardCharsets.UTF_8);
      Map<String, Object> header = new ObjectMapper().readValue(json,
          new TypeReference<Map<String, Object>>() {
          });
      long dataBase = 8 + headerLen;
      var map = new java.util.HashMap<String, TensorInfo>();
      for (var e : header.entrySet()) {
        if ("__metadata__".equals(e.getKey())) {
          continue;
        }
        Map<String, Object> t = (Map<String, Object>) e.getValue();
        if (!"F32".equals(t.get("dtype"))) {
          continue; // e.g. position_ids (I64): unused by inference
        }
        List<Long> shape = ((List<Number>) t.get("shape")).stream()
            .map(Number::longValue).toList();
        List<Number> offs = (List<Number>) t.get("data_offsets");
        map.put(e.getKey(), new TensorInfo(shape,
            dataBase + offs.get(0).longValue(), dataBase + offs.get(1).longValue()));
      }
      this.tensors = Map.copyOf(map);
      this.data = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
          .order(ByteOrder.LITTLE_ENDIAN);
    } catch (IOException e) {
      throw new EmbeddingUnavailableException("Could not read model weights.", e);
    }
  }

  private void readFully(ByteBuffer buf) throws IOException {
    buf.clear();
    while (buf.hasRemaining()) {
      if (channel.read(buf) < 0) {
        throw new IOException("truncated file");
      }
    }
    buf.flip();
  }

  public boolean has(String name) {
    return tensors.containsKey(name);
  }

  public TensorInfo info(String name) {
    TensorInfo t = tensors.get(name);
    if (t == null) {
      throw new EmbeddingUnavailableException("Model weights missing tensor: " + name);
    }
    return t;
  }

  /** Reads one row of a 2D tensor. */
  public float[] readRow(String name, int row) {
    TensorInfo t = info(name);
    if (t.shape().size() != 2) {
      throw new EmbeddingUnavailableException("Expected 2D tensor: " + name);
    }
    int cols = Math.toIntExact(t.shape().get(1));
    long base = t.dataStart() + (long) row * cols * 4L;
    float[] out = new float[cols];
    for (int i = 0; i < cols; i++) {
      out[i] = data.getFloat(Math.toIntExact(base + (long) i * 4L));
    }
    return out;
  }

  /** Reads a full 1D tensor. */
  public float[] readVector(String name) {
    TensorInfo t = info(name);
    if (t.shape().size() != 1) {
      throw new EmbeddingUnavailableException("Expected 1D tensor: " + name);
    }
    int n = Math.toIntExact(t.shape().get(0));
    float[] out = new float[n];
    for (int i = 0; i < n; i++) {
      out[i] = data.getFloat(Math.toIntExact(t.dataStart() + (long) i * 4L));
    }
    return out;
  }

  /** Reads a full 2D tensor row-major. */
  public float[][] readMatrix(String name) {
    TensorInfo t = info(name);
    if (t.shape().size() != 2) {
      throw new EmbeddingUnavailableException("Expected 2D tensor: " + name);
    }
    int rows = Math.toIntExact(t.shape().get(0));
    int cols = Math.toIntExact(t.shape().get(1));
    float[][] out = new float[rows][cols];
    long base = t.dataStart();
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        out[r][c] = data.getFloat(Math.toIntExact(base + ((long) r * cols + c) * 4L));
      }
    }
    return out;
  }

  @Override
  public void close() {
    try {
      channel.close();
    } catch (IOException ignored) {
      // shutdown path
    }
  }
}
