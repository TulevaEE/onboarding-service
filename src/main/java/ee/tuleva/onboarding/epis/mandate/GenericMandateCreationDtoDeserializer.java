package ee.tuleva.onboarding.epis.mandate;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.epis.mandate.details.*;
import ee.tuleva.onboarding.mandate.MandateType;
import java.io.IOException;

public class GenericMandateCreationDtoDeserializer
    extends JsonDeserializer<GenericMandateCreationDto<? extends MandateDetails>> {

  @Override
  public GenericMandateCreationDto<? extends MandateDetails> deserialize(
      JsonParser p, DeserializationContext ctxt) throws IOException {
    ObjectMapper mapper = (ObjectMapper) p.getCodec();
    JsonNode root = mapper.readTree(p);

    JsonNode detailsNode = root.get("details");
    MandateType type = MandateType.valueOf(detailsNode.get("mandateType").asText());

    MandateDetails details =
        switch (type) {
          case WITHDRAWAL_CANCELLATION ->
              mapper.treeToValue(detailsNode, WithdrawalCancellationMandateDetails.class);
          case EARLY_WITHDRAWAL_CANCELLATION ->
              mapper.treeToValue(detailsNode, EarlyWithdrawalCancellationMandateDetails.class);
          case TRANSFER_CANCELLATION ->
              mapper.treeToValue(detailsNode, TransferCancellationMandateDetails.class);
          case FUND_PENSION_OPENING ->
              mapper.treeToValue(detailsNode, FundPensionOpeningMandateDetails.class);
          default -> throw new IllegalArgumentException("Unknown mandateType: " + type);
        };

    return GenericMandateCreationDto.builder().details(details).build();
  }
}
