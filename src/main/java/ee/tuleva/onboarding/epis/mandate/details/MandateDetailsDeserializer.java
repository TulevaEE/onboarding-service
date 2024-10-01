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

  public static MandateType deserializeMandateTypeField(JsonNode mandateDetailsRoot) {
    MandateType type = MandateType.valueOf(mandateDetailsRoot.get("mandateType").asText());

    if (type == MandateType.UNKNOWN) {
      throw new IllegalArgumentException("Unknown mandateType: " + type);
    }

    return type;
  }

  @Override
  public MandateDetails deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    ObjectMapper mapper = (ObjectMapper) parser.getCodec();
    JsonNode root = mapper.readTree(parser);

    MandateType type = deserializeMandateTypeField(root);

    // initializing new ObjectMapper, disabling annotation inspector used to find deserializer
    // to prevent infinite loop
    ObjectMapper mapperWithoutAnnotationInspector = mapper.copy();
    mapperWithoutAnnotationInspector.setAnnotationIntrospector(customIntrospector);

    return mapperWithoutAnnotationInspector.treeToValue(root, type.getMandateDetailsClass());
  }
}
