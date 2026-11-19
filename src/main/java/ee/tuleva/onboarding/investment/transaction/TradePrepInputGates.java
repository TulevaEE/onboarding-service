package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.epis.PevaRavaCycleReports;
import ee.tuleva.onboarding.investment.epis.PevaRavaCycleRepository;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriod;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.report.ReportType;
import ee.tuleva.onboarding.investment.transaction.CommandOutcome.StoppedByInputGates;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPevaRavaReports;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPositionReport;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR45;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoR45Loaded;
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
  private final TradePrepPriceGate priceGate;
  private final TradePrepPositionGate positionGate;

  void verify(TransactionCommand command) {
    List<InputGateFailure> failures = failures(command.getFund(), command.getAsOfDate());
    if (!failures.isEmpty()) {
      throw new TradePrepInputGateException(
          new StoppedByInputGates(command.getFund(), command.getAsOfDate(), failures));
    }
  }

  List<InputGateFailure> failures(TulevaFund fund, LocalDate asOfDate) {
    LocalDate requiredReportDate = publicHolidays.previousWorkingDay(asOfDate);
    Optional<InputGateFailure> positionReport = positionReport(fund, asOfDate, requiredReportDate);
    Stream<InputGateFailure> holdingDifferences =
        positionReport.isPresent()
            ? Stream.empty()
            : positionGate.differencesFromPositionReport(fund, requiredReportDate).stream();
    return Stream.concat(
            Stream.of(
                    positionReport,
                    r45(fund, asOfDate),
                    pevaRavaReports(fund, asOfDate),
                    r16OutflowReader.missingR16(fund, asOfDate),
                    priceGate.missingPrices(fund, asOfDate))
                .flatMap(Optional::stream),
            holdingDifferences)
        .toList();
  }

  private Optional<InputGateFailure> positionReport(
      TulevaFund fund, LocalDate asOfDate, LocalDate required) {
    Optional<LocalDate> latest =
        fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, asOfDate);
    if (latest.filter(date -> !date.isBefore(required)).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(
        latest
            .<InputGateFailure>map(date -> new StalePositionReport(date, required))
            .orElseGet(() -> new MissingPositionReport(required)));
  }

  private Optional<InputGateFailure> r45(TulevaFund fund, LocalDate asOfDate) {
    if (fund.isSavingsFund() || r45ReportService.hasReportOn(asOfDate)) {
      return Optional.empty();
    }
    return Optional.of(
        r45ReportService
            .latestReportDateBefore(asOfDate)
            .<InputGateFailure>map(latest -> new MissingR45(asOfDate, latest))
            .orElseGet(() -> new NoR45Loaded(asOfDate)));
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
