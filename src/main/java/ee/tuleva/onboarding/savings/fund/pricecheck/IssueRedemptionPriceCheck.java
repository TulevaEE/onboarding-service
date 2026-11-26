package ee.tuleva.onboarding.savings.fund.pricecheck;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.ledger.UnitDealing;
import ee.tuleva.onboarding.ledger.UnitDealings;
import ee.tuleva.onboarding.savings.IssueRedemptionPriceThreshold;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class IssueRedemptionPriceCheck {

  private final UnitDealings unitDealings;
  private final FundValueQueries fundValueQueries;
  private final IssueRedemptionPriceThreshold threshold;

  PriceCheckRun run(BookingDays days) {
    BigDecimal deviationThreshold =
        threshold.issueRedemptionPriceDeviationThreshold(TKF100, days.runDay());
    Map<LocalDate, Optional<BigDecimal>> publishedNavs = new HashMap<>();
    var dealings =
        unitDealings.bookedBetween(days.from(), days.to()).stream()
            .map(dealing -> new DealingPrice(dealing, publishedNav(dealing, publishedNavs)))
            .toList();
    return new PriceCheckRun(days, deviationThreshold, dealings);
  }

  private @Nullable BigDecimal publishedNav(
      UnitDealing dealing, Map<LocalDate, Optional<BigDecimal>> publishedNavs) {
    LocalDate navDate = dealing.navDate();
    if (navDate == null) {
      return null;
    }
    return publishedNavs
        .computeIfAbsent(
            navDate,
            date -> fundValueQueries.getValueForDate(TKF100.getIsin(), date).map(FundValue::value))
        .orElse(null);
  }
}
