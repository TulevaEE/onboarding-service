package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OcfCalculationVersions {

  private final OcfSnapshotRepository ocfSnapshotRepository;

  OcfSnapshot latest(TulevaFund fund, YearMonth month) {
    return ocfSnapshotRepository
        .findByFundAndMonth(fund.getCode(), month.atDay(1))
        .orElseThrow(
            () ->
                new OcfPublicationRefusedException(
                    "No OCF calculation for the month: fund=%s, month=%s"
                        .formatted(fund.getCode(), month)));
  }

  boolean anyVersionPublished(TulevaFund fund, YearMonth month) {
    return ocfSnapshotRepository.anyVersionPublished(fund.getCode(), month.atDay(1));
  }

  OcfSnapshot unchanged(RequestedCalculation request) {
    var snapshot =
        ocfSnapshotRepository
            .findVersion(request.fund().getCode(), request.month().atDay(1), request.version())
            .orElseThrow(() -> request.refusal("No such OCF calculation"));
    if (!OcfCalculationFingerprint.of(snapshot).equals(request.calculationFingerprint())) {
      throw request.recalculatedSincePreviewed();
    }
    return snapshot;
  }
}
