package ee.tuleva.onboarding.mandate.generic;

import static ee.tuleva.onboarding.epis.mandate.details.MandateDetailsDeserializer.deserializeMandateTypeField;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import ee.tuleva.onboarding.mandate.MandateType;
import java.io.IOException;
import java.time.Instant;

public class MandateDtoDeserializer extends JsonDeserializer<MandateDto<? extends MandateDetails>> {
  @Override
  public MandateDto<? extends MandateDetails> deserialize(
      JsonParser parser, DeserializationContext context) throws IOException {
    ObjectMapper mapper = (ObjectMapper) parser.getCodec();

    JsonNode root = mapper.readTree(parser);

    JsonNode detailsNode = root.get("details");
    MandateType type = deserializeMandateTypeField(detailsNode);

    MandateDetails details = mapper.treeToValue(detailsNode, type.getMandateDetailsClass());

    var mandateDtoBuilder = MandateDto.builder();

    if (root.hasNonNull("id")) {
      mandateDtoBuilder.id(root.get("id").asLong());
    }

    if (root.hasNonNull("createdDate")) {
      mandateDtoBuilder.createdDate(Instant.parse(root.get("createdDate").asText()));
    }

    return mandateDtoBuilder.details(details).build();
  }
}
