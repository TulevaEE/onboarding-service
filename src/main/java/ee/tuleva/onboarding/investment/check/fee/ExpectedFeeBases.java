package ee.tuleva.onboarding.investment.check.fee;

import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.fees.FeeBaseValue;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.fees.PublishedFeeBase;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ExpectedFeeBases {

  private final PublishedFeeBase publishedFeeBase;

  ExpectedFeeBases(PublishedFeeBase publishedFeeBase) {
    this.publishedFeeBase = publishedFeeBase;
  }

  Optional<Map<FeeType, BigDecimal>> expectedBases(
      TulevaFund fund, List<FeeBaseValue> bases, LocalDate date) {
    return publishedFeeBase
        .find(fund, date)
        .map(expected -> bases.stream().collect(toMap(FeeBaseValue::feeType, base -> expected)));
  }
}
