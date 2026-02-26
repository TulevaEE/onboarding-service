package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import ee.tuleva.onboarding.mandate.MandateType;
import java.time.Instant;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class MandateDtoDeserializer
    extends ValueDeserializer<MandateDto<? extends MandateDetails>> {
  @Override
  public MandateDto<? extends MandateDetails> deserialize(
      JsonParser parser, DeserializationContext ctxt) {
    JsonNode root = ctxt.readTree(parser);

    JsonNode detailsNode = root.get("details");
    MandateType type = MandateType.valueOf(detailsNode.get("mandateType").asText());
    if (type == MandateType.UNKNOWN) {
      throw new IllegalArgumentException("Unknown mandateType: " + type);
    }

    MandateDetails details = ctxt.readTreeAsValue(detailsNode, type.getMandateDetailsClass());

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
