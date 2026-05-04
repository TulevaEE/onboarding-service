package ee.tuleva.onboarding.investment.check.tracking;

import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NavTrackingDifferenceGate {

  private final TrackingDifferenceService trackingDifferenceService;

  public Optional<String> check(TulevaFund fund, LocalDate navDate) {
    try {
      var results = trackingDifferenceService.checkFund(fund, navDate);
      var breaches = results.stream().filter(TrackingDifferenceResult::breach).toList();
      if (!breaches.isEmpty()) {
        var details =
            breaches.stream()
                .map(
                    r ->
                        "%s TD=%s (fund=%s, benchmark=%s)"
                            .formatted(
                                r.checkType(),
                                r.trackingDifference().toPlainString(),
                                r.fundReturn().toPlainString(),
                                r.benchmarkReturn().toPlainString()))
                .collect(joining("; "));
        return Optional.of(
            "TD breach: fund=%s, date=%s, %s".formatted(fund.getCode(), navDate, details));
      }
      return Optional.empty();
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      log.warn("TD gate incomplete price data, proceeding: fund={}, date={}", fund, navDate);
      return Optional.empty();
    } catch (Exception e) {
      log.warn("TD gate error, proceeding: fund={}, date={}", fund, navDate, e);
      return Optional.empty();
    }
  }
}
