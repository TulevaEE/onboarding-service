package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK_MODEL;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrackingDifferenceQueryService {

  private final TrackingDifferenceEventRepository eventRepository;
  private final TrackingDifferenceCalculator calculator;

  public Optional<BenchmarkModelTrackingDifference> findLatestBenchmarkModel(
      TulevaFund fund, LocalDate navDate) {
    return eventRepository
        .findDeduplicatedEventsForPeriod(fund, BENCHMARK_MODEL, navDate, navDate)
        .stream()
        .findFirst()
        .map(
            event ->
                new BenchmarkModelTrackingDifference(
                    event.getTrackingDifference(), calculator.breachThreshold(navDate)));
  }
}
