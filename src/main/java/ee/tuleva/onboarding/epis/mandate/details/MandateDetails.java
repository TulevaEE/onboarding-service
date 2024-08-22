package ee.tuleva.onboarding.epis.mandate.details;

import com.fasterxml.jackson.databind.JsonNode;
import ee.tuleva.onboarding.mandate.MandateType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public abstract class MandateDetails {
  protected final MandateType mandateType;
  // public static MandateDetails fromJsonNode(JsonNode detailsNode);
}
