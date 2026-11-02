package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;

interface RequestedCalculation {

  TulevaFund fund();

  YearMonth month();

  int version();

  String calculationFingerprint();

  default OcfPublicationRefusedException refusal(String description) {
    return new OcfPublicationRefusedException(
        "%s: fund=%s, month=%s, version=%d"
            .formatted(description, fund().getCode(), month(), version()));
  }
}
