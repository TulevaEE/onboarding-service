package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;

interface RequestedCalculation {

  TulevaFund fund();

  YearMonth month();

  int version();

  String calculationFingerprint();

  default OcfPublicationRefusedException refusal(String description) {
    return new OcfPublicationRefusedException(described(description));
  }

  default OcfPublicationRefusedException recalculatedSincePreviewed() {
    return refusal("The OCF calculation was recalculated since it was previewed");
  }

  default OcfPublicationRefusedException refusal(String description, Throwable cause) {
    return new OcfPublicationRefusedException(described(description), cause);
  }

  private String described(String description) {
    return "%s: fund=%s, month=%s, version=%d"
        .formatted(description, fund().getCode(), month(), version());
  }
}
