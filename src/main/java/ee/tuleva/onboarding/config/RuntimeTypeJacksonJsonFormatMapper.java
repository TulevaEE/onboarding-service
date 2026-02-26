package ee.tuleva.onboarding.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import org.hibernate.type.format.FormatMapperCreationContext;

/**
 * Fixes Hibernate's {@link org.hibernate.type.format.jackson.JacksonJsonFormatMapper} which
 * serializes using the declared field type, losing subclass fields for polymorphic types like
 * {@link ee.tuleva.onboarding.epis.mandate.details.MandateDetails}.
 *
 * <p>This mapper uses {@code objectMapper.writeValueAsString(value)} (runtime type) instead of
 * {@code objectMapper.writerFor(declaredType).writeValueAsString(value)}.
 */
public class RuntimeTypeJacksonJsonFormatMapper extends AbstractJsonFormatMapper {

  private final ObjectMapper objectMapper;

  public RuntimeTypeJacksonJsonFormatMapper() {
    this.objectMapper = new ObjectMapper().findAndRegisterModules();
  }

  public RuntimeTypeJacksonJsonFormatMapper(FormatMapperCreationContext context) {
    this();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected <T> T fromString(CharSequence charSequence, Type type) {
    try {
      return (T) objectMapper.readValue(charSequence.toString(), objectMapper.constructType(type));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Could not deserialize string to java type: " + type, e);
    }
  }

  @Override
  protected <T> String toString(T value, Type type) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Could not serialize object of java type: " + type, e);
    }
  }
}
