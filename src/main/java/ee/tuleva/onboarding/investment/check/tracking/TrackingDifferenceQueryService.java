package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;

import ee.tuleva.onboarding.investment.TrackingCheckType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrackingDifferenceQueryService {

  private final TrackingDifferenceEventRepository eventRepository;
  private final TrackingDifferenceCalculator calculator;

  public Optional<TrackingDifferenceSummary> findLatestModelPortfolio(
      TulevaFund fund, LocalDate navDate) {
    return findLatest(fund, MODEL_PORTFOLIO, navDate);
  }

  public Optional<TrackingDifferenceSummary> findLatestBenchmarkModel(
      TulevaFund fund, LocalDate navDate) {
    return findLatest(fund, BENCHMARK_MODEL, navDate);
  }

  private Optional<TrackingDifferenceSummary> findLatest(
      TulevaFund fund, TrackingCheckType checkType, LocalDate navDate) {
    return eventRepository
        .findDeduplicatedEventsForPeriod(fund, checkType, navDate, navDate)
        .stream()
        .findFirst()
        .map(
            event ->
                new TrackingDifferenceSummary(
                    event.getTrackingDifference(), calculator.breachThreshold(navDate)));
  }
}
