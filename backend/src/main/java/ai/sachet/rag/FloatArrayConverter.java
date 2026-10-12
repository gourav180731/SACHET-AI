package ai.sachet.rag;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** JPA mapping for {@code float[]} embeddings (see {@link FloatArrayCodec}). */
@Converter
public class FloatArrayConverter implements AttributeConverter<float[], String> {

  @Override
  public String convertToDatabaseColumn(float[] attribute) {
    return FloatArrayCodec.encode(attribute);
  }

  @Override
  public float[] convertToEntityAttribute(String dbData) {
    return FloatArrayCodec.decode(dbData);
  }
}
