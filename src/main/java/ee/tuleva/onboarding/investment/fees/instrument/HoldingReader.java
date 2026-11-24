package ee.tuleva.onboarding.investment.fees.instrument;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.OpenTradeSource;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HoldingReader {

  private final FundNavQueryService navQueries;
  private final FundPositionRepository positions;
  private final OpenTradeSource openTradeSource;

  DayHoldings on(LocalDate day) {
    return new DayHoldings(day, navQueries, positions, openTradeSource);
  }
}
