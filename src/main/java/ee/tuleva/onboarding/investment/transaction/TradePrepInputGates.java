package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.CommandOutcome.StoppedByInputGates;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPositionReport;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR45;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoR45Loaded;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.StalePositionReport;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradePrepInputGates {

  private final FundPositionRepository fundPositionRepository;
  private final PublicHolidays publicHolidays;
  private final R45ReportService r45ReportService;
  private final R16OutflowReader r16OutflowReader;
  private final TradePrepPriceGate priceGate;
  private final TradePrepPositionGate positionGate;
  private final TradePrepPevaRavaGate pevaRavaGate;

  GateCheck verify(TransactionCommand command) {
    GateCheck check =
        check(command.getFund(), command.getAsOfDate(), TradePrepOverrides.of(command));
    if (!check.failures().isEmpty()) {
      throw new TradePrepInputGateException(
          new StoppedByInputGates(command.getFund(), command.getAsOfDate(), check.failures()));
    }
    return check;
  }

  GateCheck check(TulevaFund fund, LocalDate asOfDate, TradePrepOverrides overrides) {
    LocalDate requiredReportDate = publicHolidays.previousWorkingDay(asOfDate);
    Optional<LocalDate> latestReportDate =
        fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, asOfDate);
    Optional<LocalDate> currentReportDate =
        latestReportDate.filter(date -> !date.isBefore(requiredReportDate));
    Optional<InputGateFailure> positionReport =
        currentReportDate.isPresent()
            ? Optional.empty()
            : Optional.of(
                latestReportDate
                    .<InputGateFailure>map(
                        date -> new StalePositionReport(date, requiredReportDate))
                    .orElseGet(() -> new MissingPositionReport(requiredReportDate)));
    Optional<PositionCheck> holdings =
        currentReportDate.map(date -> positionGate.check(fund, date, overrides));
    PriceCheck prices = priceGate.check(fund, asOfDate, overrides);
    List<InputGateFailure> failures =
        Stream.of(
                Stream.of(
                        positionReport,
                        r45(fund, asOfDate),
                        pevaRavaGate.missingReports(fund, asOfDate),
                        r16OutflowReader.missingR16(fund, asOfDate))
                    .flatMap(Optional::stream),
                prices.failures().stream(),
                holdings.stream().flatMap(check -> check.failures().stream()))
            .flatMap(Function.identity())
            .toList();
    return new GateCheck(
        failures,
        prices.pricing(),
        prices.priceOverrides(),
        holdings.map(PositionCheck::acceptedDifferences).orElse(List.of()),
        prices.confirmedDeviations(),
        prices.withoutIndexPriceHistory());
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
}
