package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.partitioningBy;

import ee.tuleva.onboarding.investment.fees.rate.AccrualRate;
import ee.tuleva.onboarding.investment.fees.rate.AccrualRates;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class InstrumentFeeAccrualService {

  static final int RECALCULATED_DAYS_AS_LATE_REPORTS_AND_PRICES_ARRIVE = 7;

  private final AccrualRates accrualRates;
  private final ProviderAgreementRepository providerAgreementRepository;
  private final HoldingReader holdingReader;
  private final DayPriceReader dayPriceReader;
  private final InstrumentFeeAccrualRepository accrualRepository;
  private final Clock clock;

  AccrualRun accrueTheRecentDays() {
    var yesterday = yesterday();
    return accrue(firstDayToRecalculate(yesterday), yesterday);
  }

  AccrualRun accrueSince(LocalDate firstDay) {
    return accrue(firstDay, yesterday());
  }

  private LocalDate firstDayToRecalculate(LocalDate yesterday) {
    var lateInputsWindowStart =
        yesterday.minusDays(RECALCULATED_DAYS_AS_LATE_REPORTS_AND_PRICES_ARRIVE - 1L);
    var previousMonth = YearMonth.from(yesterday).minusMonths(1);
    var previousMonthAwaitsItsResolvedRates =
        accrualRepository.holdsProvisionalAccrualsBetween(
            previousMonth.atDay(1), previousMonth.atEndOfMonth());
    return previousMonthAwaitsItsResolvedRates
            && previousMonth.atDay(1).isBefore(lateInputsWindowStart)
        ? previousMonth.atDay(1)
        : lateInputsWindowStart;
  }

  private AccrualRun accrue(LocalDate firstDay, LocalDate lastDay) {
    var accruedOrNot =
        firstDay.datesUntil(lastDay.plusDays(1)).collect(partitioningBy(this::accrued));
    var run =
        new AccrualRun(
            requireNonNull(accruedOrNot.get(true)),
            requireNonNull(accruedOrNot.get(false)),
            accrualRates.isinsWithoutAProviderAgreementOn(lastDay));
    log.info(
        "Instrument fee accruals calculated: firstDay={}, lastDay={}, accruedDays={},"
            + " failedDays={}, isinsWithoutAProviderAgreement={}",
        firstDay,
        lastDay,
        run.accruedDays().size(),
        run.failedDays(),
        run.isinsWithoutAProviderAgreement());
    return run;
  }

  private boolean accrued(LocalDate day) {
    List<RatedAgreement> agreements;
    try {
      agreements = ratedAgreementsOn(day);
    } catch (RuntimeException e) {
      log.error("Instrument fee accrual failed, the day's stored accruals stay: day={}", day, e);
      return false;
    }
    var holdings = holdingReader.on(day);
    return Arrays.stream(TulevaFund.values())
        .map(fund -> fundAccrued(fund, day, agreements, holdings))
        .reduce(true, Boolean::logicalAnd);
  }

  private List<RatedAgreement> ratedAgreementsOn(LocalDate day) {
    var versions = providerAgreementRepository.findVersionsValidOn(day);
    return accrualRates.on(day).stream()
        .flatMap(
            rate ->
                versionOf(rate, versions, day)
                    .map(version -> new RatedAgreement(rate, version))
                    .stream())
        .toList();
  }

  private static Optional<ProviderAgreementVersion> versionOf(
      AccrualRate rate, Map<Long, ProviderAgreementVersion> versions, LocalDate day) {
    var version = Optional.ofNullable(versions.get(rate.providerAgreementId()));
    if (version.isEmpty()) {
      log.warn(
          "Instrument not accrued, its provider agreement has no version valid on the day:"
              + " isin={}, providerAgreementId={}, day={}",
          rate.isin(),
          rate.providerAgreementId(),
          day);
    }
    return version;
  }

  private boolean fundAccrued(
      TulevaFund fund, LocalDate day, List<RatedAgreement> agreements, DayHoldings holdings) {
    try {
      var accruals =
          agreements.stream()
              .flatMap(agreement -> accrual(fund, day, agreement, holdings).stream())
              .toList();
      accrualRepository.replaceTheFundsDay(fund, day, accruals, clock.instant());
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Instrument fee accrual failed for the fund, its stored accruals for the day stay:"
              + " fund={}, day={}",
          fund,
          day,
          e);
      return false;
    }
  }

  private Optional<InstrumentFeeAccrual> accrual(
      TulevaFund fund, LocalDate day, RatedAgreement agreement, DayHoldings holdings) {
    var isin = agreement.rate().isin();
    return holdings
        .of(fund, isin, UnitsBasis.of(agreement.version().method()))
        .filter(holding -> held(holding, fund, isin, day))
        .flatMap(
            holding ->
                priceOf(fund, isin, day, holding)
                    .map(
                        price ->
                            new InstrumentFeeAccrual(
                                fund, day, agreement.rate(), agreement.version(), holding, price)));
  }

  private static boolean held(Holding holding, TulevaFund fund, String isin, LocalDate day) {
    if (holding.units().signum() < 0) {
      log.warn(
          "Instrument not accrued, its units are negative: fund={}, isin={}, day={}, units={},"
              + " unitsSource={}, unitsDate={}",
          fund,
          isin,
          day,
          holding.units(),
          holding.source(),
          holding.unitsDate());
    }
    return holding.units().signum() > 0;
  }

  private Optional<InstrumentPrice> priceOf(
      TulevaFund fund, String isin, LocalDate day, Holding holding) {
    var price = dayPriceReader.priceOn(isin, day, holding);
    if (price.isEmpty()) {
      log.warn(
          "Instrument held but not accrued, it has no price for the day: fund={}, isin={}, day={}",
          fund,
          isin,
          day);
    }
    return price;
  }

  private LocalDate yesterday() {
    return LocalDate.ofInstant(clock.instant(), ZoneId.of(TIMEZONE)).minusDays(1);
  }

  private record RatedAgreement(AccrualRate rate, ProviderAgreementVersion version) {}
}
