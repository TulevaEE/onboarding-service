package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import ee.tuleva.onboarding.analytics.FundUnitCounts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FundBalanceUnitCounts implements FundUnitCounts {

  private final FundBalanceRepository fundBalanceRepository;

  @Override
  public Optional<BigDecimal> totalUnitsAsOf(String isin, LocalDate date) {
    return fundBalanceRepository
        .findFirstByIsinAndRequestDateLessThanEqualOrderByRequestDateDesc(isin, date)
        .map(balance -> balance.getCountUnits().add(balance.getCountUnitsFm()));
  }
}
