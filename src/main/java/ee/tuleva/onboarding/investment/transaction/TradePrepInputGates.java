package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.epis.PevaRavaCycleEntity;
import ee.tuleva.onboarding.investment.epis.PevaRavaCycleRepository;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriod;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.report.ReportType;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPevaRavaReports;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR45;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.StalePositionReport;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradePrepInputGates {

  private final FundPositionRepository fundPositionRepository;
  private final PublicHolidays publicHolidays;
  private final R45ReportService r45ReportService;
  private final PevaRavaPeriodService pevaRavaPeriodService;
  private final PevaRavaCycleRepository pevaRavaCycleRepository;
  private final R16OutflowReader r16OutflowReader;
  private final TradePrepInputGateNotifier notifier;

  void verify(TransactionCommand command) {
    List<InputGateFailure> failures = failures(command.getFund(), command.getAsOfDate());
    if (failures.isEmpty()) {
      return;
    }
    notifier.notifyStopped(command.getFund(), command.getAsOfDate(), failures);
    throw new TradePrepInputGateException(command.getFund(), command.getAsOfDate(), failures);
  }

  List<InputGateFailure> failures(TulevaFund fund, LocalDate asOfDate) {
    return Stream.of(
            positionReport(fund, asOfDate),
            r45(fund, asOfDate),
            pevaRavaReports(fund, asOfDate),
            r16OutflowReader.missingR16(fund, asOfDate))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<InputGateFailure> positionReport(TulevaFund fund, LocalDate asOfDate) {
    LocalDate required = publicHolidays.previousWorkingDay(asOfDate);
    Optional<LocalDate> latest =
        fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, asOfDate);
    if (latest.filter(date -> !date.isBefore(required)).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(new StalePositionReport(latest.orElse(null), required));
  }

  private Optional<InputGateFailure> r45(TulevaFund fund, LocalDate asOfDate) {
    if (fund.isSavingsFund() || r45ReportService.hasReportOn(asOfDate)) {
      return Optional.empty();
    }
    return Optional.of(
        new MissingR45(asOfDate, r45ReportService.latestReportDateBefore(asOfDate).orElse(null)));
  }

  private Optional<InputGateFailure> pevaRavaReports(TulevaFund fund, LocalDate asOfDate) {
    return pevaRavaPeriodService
        .getCurrentPeriod(asOfDate)
        .filter(period -> period.countsFlowsOf(fund))
        .flatMap(period -> missingPevaRavaReports(fund, period));
  }

  private Optional<InputGateFailure> missingPevaRavaReports(
      TulevaFund fund, PevaRavaPeriod period) {
    List<ReportType> missing =
        pevaRavaCycleRepository
            .findByExecDate(period.cycle().execDate())
            .map(PevaRavaCycleEntity::missingReports)
            .orElse(List.of(R17_PEVA, R21_RAVA));
    if (missing.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new MissingPevaRavaReports(
            missing, period.cycle().execDate(), period.timelineFor(fund).dActiveDate()));
  }
}
