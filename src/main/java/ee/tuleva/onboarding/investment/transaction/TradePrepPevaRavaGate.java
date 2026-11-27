package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;

import ee.tuleva.onboarding.investment.epis.PevaRavaCycleReports;
import ee.tuleva.onboarding.investment.epis.PevaRavaCycleRepository;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriod;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import ee.tuleva.onboarding.investment.report.ReportType;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPevaRavaReports;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradePrepPevaRavaGate {

  private final PevaRavaPeriodService pevaRavaPeriodService;
  private final PevaRavaCycleRepository pevaRavaCycleRepository;

  Optional<InputGateFailure> missingReports(TulevaFund fund, LocalDate asOfDate) {
    return pevaRavaPeriodService
        .getCurrentPeriod(asOfDate)
        .filter(period -> period.countsFlowsOf(fund))
        .flatMap(period -> missingPevaRavaReports(fund, period));
  }

  private Optional<InputGateFailure> missingPevaRavaReports(
      TulevaFund fund, PevaRavaPeriod period) {
    List<ReportType> missing =
        pevaRavaCycleRepository
            .findReportsByExecDate(period.cycle().execDate())
            .map(PevaRavaCycleReports::missingReports)
            .orElse(List.of(R17_PEVA, R21_RAVA));
    if (missing.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new MissingPevaRavaReports(
            missing, period.cycle().execDate(), period.timelineFor(fund).dActiveDate()));
  }
}
