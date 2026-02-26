package ee.tuleva.onboarding.epis.mandate.details;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import ee.tuleva.onboarding.mandate.MandateType;
import java.io.IOException;

// Used by Hibernate for deserializing MandateDetails JSONB columns.
// MandateDtoDeserializer (Jackson 3) handles HTTP request deserialization separately.
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

    // Copy mapper without the annotation introspector to prevent infinite recursion —
    // otherwise treeToValue would re-trigger this deserializer via @JsonDeserialize on
    // MandateDetails.
    ObjectMapper mapperWithoutAnnotationInspector = mapper.copy();
    mapperWithoutAnnotationInspector.setAnnotationIntrospector(customIntrospector);

    return mapperWithoutAnnotationInspector.treeToValue(root, type.getMandateDetailsClass());
  }
}
