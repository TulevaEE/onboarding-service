package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;

import ee.tuleva.onboarding.investment.TrackingCheckType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TdAttributionStaleness {

  private static final Set<TrackingCheckType> CHECK_TYPES_THE_ATTRIBUTION_READS =
      Set.of(MODEL_PORTFOLIO, BENCHMARK_MODEL);

  private final TrackingDifferenceEventRepository eventRepository;
  private final PeriodicTdAttributionRepository attributionRepository;

  boolean needsRewrite(TulevaFund fund, YearMonth month) {
    var lastCheckWrittenAt =
        eventRepository.findLatestWrittenAt(
            fund, CHECK_TYPES_THE_ATTRIBUTION_READS, month.atDay(1), month.atEndOfMonth());
    if (lastCheckWrittenAt == null) {
      return false;
    }
    return attributionRepository
        .findWrittenAt(fund, month.atDay(1), month.atEndOfMonth(), MONTHLY)
        .map(attributionWrittenAt -> attributionWrittenAt.isBefore(lastCheckWrittenAt))
        .orElse(true);
  }
}
