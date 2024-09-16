package ee.tuleva.onboarding.epis.mandate.details;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import ee.tuleva.onboarding.mandate.MandateType;
import java.io.IOException;

// MandateDetails deserializer is required for reading from database
// GenericMandateCreationDTO is required for reading from request
public class MandateDetailsDeserializer extends JsonDeserializer<MandateDetails> {

  private final JacksonAnnotationIntrospector customIntrospector =
      new JacksonAnnotationIntrospector() {
        @Override
        public Object findDeserializer(Annotated a) {
          return null;
        }
      };

  @Override
  public MandateDetails deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    ObjectMapper mapper = (ObjectMapper) p.getCodec();
    JsonNode root = mapper.readTree(p);

    MandateType type = MandateType.valueOf(root.get("mandateType").asText());

    if (type == MandateType.UNKNOWN) {
      throw new IllegalArgumentException("Unknown mandateType: " + type);
    }

    // initializing new ObjectMapper, disabling annotation inspector used to find deserializer
    // to prevent infinite loop
    ObjectMapper anotherMapper = mapper.copy();
    anotherMapper.setAnnotationIntrospector(customIntrospector);

    return anotherMapper.treeToValue(root, type.getMandateDetailsClass());
  }
}
