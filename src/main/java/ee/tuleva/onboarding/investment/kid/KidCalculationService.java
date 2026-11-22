package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.KidCalendar.ART_8_3_SCENARIO_HISTORY_STARTS_WITH_THE_MONTH;
import static ee.tuleva.onboarding.investment.kid.KidRefusalCause.NO_OCF_IN_FORCE;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class KidCalculationService {

  private final KidFunds kidFunds;
  private final KidFiguresService kidFiguresService;
  private final OcfPublicationService ocfPublications;
  private final KidCalculationRepository repository;
  private final Clock clock;

  StoredKidCalculation calculateAndStore(TulevaFund fund, YearMonth month) {
    refuseUnlessCalculable(fund, month);
    return store(
        calculation(
            fund,
            month,
            monthEndPrices(fund, today()).missingCloseReason(month).stream().toList()));
  }

  List<KidFundRun> calculateTheMissingMonths() {
    return kidFunds.funds().keySet().stream().map(this::calculateTheMissingMonthsAlone).toList();
  }

  private KidFundRun calculateTheMissingMonthsAlone(TulevaFund fund) {
    try {
      return calculateTheMissingMonths(fund);
    } catch (RuntimeException e) {
      log.error("KID calculation failed: fund={}", fund, e);
      return KidFundRun.failed(fund, e);
    }
  }

  private KidFundRun calculateTheMissingMonths(TulevaFund fund) {
    var today = today();
    var lastMonthEnded = KidCalendar.lastMonthEnded(today);
    var complete =
        repository.findMonthsPublishedOrCompleteInTheirLatestVersion(
            fund, ART_8_3_SCENARIO_HISTORY_STARTS_WITH_THE_MONTH, lastMonthEnded);
    var prices = monthEndPrices(fund, today);
    var outcomes =
        Stream.iterate(
                ART_8_3_SCENARIO_HISTORY_STARTS_WITH_THE_MONTH,
                month -> !month.isAfter(lastMonthEnded),
                month -> month.plusMonths(1))
            .filter(month -> !complete.contains(month))
            .map(month -> calculateTheMonthAlone(prices, month))
            .toList();
    return KidFundRun.ran(
        fund,
        outcomes.stream()
            .<StoredKidCalculation>mapMulti(
                (outcome, next) -> {
                  if (outcome instanceof MonthOutcome.Stored(var calculation)) {
                    next.accept(calculation);
                  }
                })
            .toList(),
        outcomes.stream()
            .<KidFailedMonth>mapMulti(
                (outcome, next) -> {
                  if (outcome instanceof MonthOutcome.Failed(var failed)) {
                    next.accept(failed);
                  }
                })
            .toList());
  }

  private MonthOutcome calculateTheMonthAlone(MonthEndPrices prices, YearMonth month) {
    try {
      return calculateIfItsPricesAreDue(prices, month)
          .<MonthOutcome>map(MonthOutcome.Stored::new)
          .orElseGet(MonthOutcome.NothingStored::new);
    } catch (RuntimeException e) {
      log.error("KID calculation failed: fund={}, month={}", prices.fund(), month, e);
      return new MonthOutcome.Failed(KidFailedMonth.of(month, e));
    }
  }

  private sealed interface MonthOutcome {
    record Stored(StoredKidCalculation calculation) implements MonthOutcome {}

    record NothingStored() implements MonthOutcome {}

    record Failed(KidFailedMonth month) implements MonthOutcome {}
  }

  private Optional<StoredKidCalculation> calculateIfItsPricesAreDue(
      MonthEndPrices prices, YearMonth month) {
    if (prices.closes(month)) {
      return storeUnlessRepeated(calculation(prices.fund(), month, List.of()));
    }
    var stopped = prices.stoppedReason();
    if (stopped.isEmpty()) {
      log.info(
          "KID calculation waits for the month-end close: fund={}, month={}, latestClose={}",
          prices.fund(),
          month,
          prices.latestClose());
      return Optional.empty();
    }
    return storeUnlessRepeated(calculation(prices.fund(), month, List.of(stopped.get())));
  }

  private KidCalculation calculation(
      TulevaFund fund, YearMonth month, List<String> missingMonthEndPrices) {
    var ocf = ocfBasis(fund, month);
    if (ocf.isEmpty()) {
      return KidCalculation.refused(
          fund,
          month,
          Stream.concat(missingMonthEndPrices.stream(), Stream.of(noOcfInForce(fund, month)))
              .toList());
    }
    if (!missingMonthEndPrices.isEmpty()) {
      return KidCalculation.refused(fund, month, ocf.get(), missingMonthEndPrices);
    }
    return calculate(fund, month, ocf.get());
  }

  private KidCalculation calculate(TulevaFund fund, YearMonth month, OcfBasis ocf) {
    try {
      return KidCalculation.complete(
          fund, month, ocf, kidFiguresService.figures(fund, month, ocf.ongoingCharges()));
    } catch (KidCalculationRefusedException refusal) {
      return KidCalculation.refused(fund, month, ocf, List.of(reason(refusal)));
    }
  }

  private Optional<OcfBasis> ocfBasis(TulevaFund fund, YearMonth month) {
    return ocfPublications
        .figureInForceOn(fund, month.atEndOfMonth())
        .map(
            kid ->
                ocfPublications
                    .calculationOf(kid)
                    .map(calculation -> OcfBasis.calculated(kid, calculation))
                    .orElseGet(() -> OcfBasis.given(kid)));
  }

  private MonthEndPrices monthEndPrices(TulevaFund fund, LocalDate today) {
    return new MonthEndPrices(fund, kidFiguresService.latestCloseDate(fund).orElse(null), today);
  }

  private LocalDate today() {
    return KidCalendar.today(clock);
  }

  private Optional<StoredKidCalculation> storeUnlessRepeated(KidCalculation calculation) {
    if (!calculation.complete()
        && repository
            .findLatest(calculation.fund(), calculation.month())
            .filter(calculation::repeats)
            .isPresent()) {
      log.info(
          "KID calculation refused as before, not stored again: fund={}, month={}, reasons={}",
          calculation.fund(),
          calculation.month(),
          calculation.refusalReasons());
      return Optional.empty();
    }
    return Optional.of(store(calculation));
  }

  private StoredKidCalculation store(KidCalculation calculation) {
    var stored = repository.save(calculation, clock.instant());
    log.info(
        "KID calculation stored: fund={}, month={}, version={}, complete={}, reasons={}",
        stored.fund(),
        stored.month(),
        stored.version(),
        stored.complete(),
        stored.refusalReasons());
    return stored;
  }

  private void refuseUnlessCalculable(TulevaFund fund, YearMonth month) {
    if (!kidFunds.funds().containsKey(fund)) {
      throw new InvalidKidRequestException("No KID for the fund: fund=" + fund);
    }
    if (month.isBefore(ART_8_3_SCENARIO_HISTORY_STARTS_WITH_THE_MONTH)) {
      throw new InvalidKidRequestException(
          "Month before the scenario history starts: month=%s, historyStarts=%s"
              .formatted(month, ART_8_3_SCENARIO_HISTORY_STARTS_WITH_THE_MONTH));
    }
    if (month.isAfter(KidCalendar.lastMonthEnded(today()))) {
      throw new InvalidKidRequestException("Month not ended yet: month=" + month);
    }
  }

  private static String noOcfInForce(TulevaFund fund, YearMonth month) {
    return NO_OCF_IN_FORCE.reason("fund=%s, monthEnd=%s".formatted(fund, month.atEndOfMonth()));
  }

  private static String reason(KidCalculationRefusedException refusal) {
    return requireNonNull(refusal.getMessage(), "A KID refusal names its reason");
  }
}
