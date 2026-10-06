package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;

import ee.tuleva.onboarding.fund.ManagementFeeRates;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ManagementFeeRateTable implements ManagementFeeRates {

  private final FeeRateRepository feeRateRepository;

  @Override
  public Optional<BigDecimal> rateInForceOn(TulevaFund fund, LocalDate date) {
    return feeRateRepository.findValidRate(fund, MANAGEMENT, date).map(FeeRate::annualRate);
  }
}
