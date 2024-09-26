package ee.tuleva.onboarding.mandate.generic;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetailsDeserializer;
import ee.tuleva.onboarding.mandate.MandateType;
import java.io.IOException;

public class MandateDtoDeserializer extends JsonDeserializer<MandateDto<? extends MandateDetails>> {

  public static MandateDetails deserializeDetailsField(JsonParser p) throws IOException {
    ObjectMapper mapper = (ObjectMapper) p.getCodec();

    JsonNode root = mapper.readTree(p);

    JsonNode detailsNode = root.get("details");
    MandateType type = MandateDetailsDeserializer.deserializeMandateTypeField(detailsNode);

    return mapper.treeToValue(detailsNode, type.getMandateDetailsClass());
  }

  @Override
  public MandateDto<? extends MandateDetails> deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    MandateDetails details = deserializeDetailsField(p);

    return MandateDto.builder().details(details).build();
  }
}
