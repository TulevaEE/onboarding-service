package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.CheckType.OWNERSHIP;
import static ee.tuleva.onboarding.investment.check.limit.StaleFundSizes.HOLDINGS;
import static ee.tuleva.onboarding.investment.check.limit.StaleFundSizes.STALE_SIZES;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;

import ee.tuleva.onboarding.investment.check.limit.HeldSecurities.HeldSecurity;
import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.NotChecked;
import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.Result;
import ee.tuleva.onboarding.investment.check.limit.UnderlyingFunds.SizeInEur;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OwnershipLimitCheckService {

  private final OwnershipLimitProvider ownershipLimitProvider;
  private final HeldSecurities heldSecurities;
  private final UnderlyingFunds underlyingFunds;
  private final OwnershipLimitChecker ownershipLimitChecker;
  private static final String COVERED_EVERY_HOLDING = "coveredEveryHolding";
  private static final String SIZES_COMPARED_WITH_THE_MONTH_BEFORE =
      "sizesComparedWithTheMonthBefore";
  private static final String ISHARES_DEVELOPED_WORLD_SCREENED_INDEX_FUND = "IE00BFG1TM61";
  private static final Map<TulevaFund, Map<String, String>> LEFT_OUT_BY_DESIGN =
      Map.of(
          TKF100,
          Map.of(
              ISHARES_DEVELOPED_WORLD_SCREENED_INDEX_FUND,
              "EODHD has no total assets, and the fund is too large for TKF100 to own 25% of it"));

  private final LimitCheckEventWriter limitCheckEventWriter;
  private final LimitCheckEventRepository limitCheckEventRepository;
  private final StaleFundSizes staleFundSizes;

  boolean everyFundIsChecked(YearMonth month) {
    var limits = ownershipLimitProvider.findAllLatestAsOf(month.atEndOfMonth());
    return !limits.isEmpty()
        && limits.stream().allMatch(limit -> checkedInFull(limit.fund(), month));
  }

  private boolean checkedInFull(TulevaFund fund, YearMonth month) {
    return limitCheckEventRepository
        .findByFundAndCheckTypeAndCheckDateBetween(
            fund, OWNERSHIP, month.atDay(1), month.atEndOfMonth())
        .stream()
        .map(LimitCheckEvent::getResult)
        .anyMatch(
            result ->
                Boolean.TRUE.equals(result.get(COVERED_EVERY_HOLDING))
                    && !Boolean.FALSE.equals(result.get(SIZES_COMPARED_WITH_THE_MONTH_BEFORE)));
  }

  OwnershipCheckRun checkMonthEnd(YearMonth month) {
    var results = new ArrayList<Result>();
    var fundsNotChecked = new ArrayList<NotChecked>();

    ownershipLimitProvider
        .findAllLatestAsOf(month.atEndOfMonth())
        .forEach(
            limit -> {
              var fund = limit.fund();
              var checkDate = heldSecurities.lastPositionDateOnOrBefore(fund, month.atEndOfMonth());
              if (checkDate.isEmpty() || !YearMonth.from(checkDate.get()).equals(month)) {
                log.warn(
                    "No position data in the month for ownership check: fund={}, month={},"
                        + " latestPositionDate={}",
                    fund,
                    month,
                    checkDate.orElse(null));
                fundsNotChecked.add(new NotChecked(fund, noPositionsIn(month, checkDate)));
                return;
              }
              try {
                results.add(checkFund(fund, checkDate.get(), limit));
              } catch (Exception e) {
                log.error(
                    "Ownership check failed: fund={}, checkDate={}", fund, checkDate.get(), e);
                fundsNotChecked.add(new NotChecked(fund, "check failed: " + describe(e)));
              }
            });

    return new OwnershipCheckRun(month, List.copyOf(results), List.copyOf(fundsNotChecked));
  }

  private static String noPositionsIn(YearMonth month, Optional<LocalDate> latestPositionDate) {
    return latestPositionDate
        .map(date -> "no positions in %s, the latest are from %s".formatted(month, date))
        .orElse("no positions in %s or before".formatted(month));
  }

  static String describe(Exception e) {
    var message = e.getMessage();
    return message == null
        ? e.getClass().getSimpleName()
        : e.getClass().getSimpleName() + ": " + message;
  }

  private Result checkFund(TulevaFund fund, LocalDate checkDate, OwnershipLimit limit) {
    var assessments =
        heldSecurities.on(fund, checkDate).stream()
            .map(held -> assessOrLeaveUnverified(held, checkDate, limit))
            .toList();
    var holdings =
        assessments.stream()
            .filter(OwnershipBreach.class::isInstance)
            .map(OwnershipBreach.class::cast)
            .toList();
    var result =
        staleFundSizes.comparedWithTheMonthBefore(
            new Result(
                fund,
                checkDate,
                holdings,
                assessments.stream()
                    .filter(UnverifiedHolding.class::isInstance)
                    .map(UnverifiedHolding.class::cast)
                    .toList(),
                assessments.stream()
                    .filter(LeftOutHolding.class::isInstance)
                    .map(LeftOutHolding.class::cast)
                    .toList(),
                List.of()));
    limitCheckEventWriter.replaceEvents(fund, checkDate, List.of(event(result)));
    return result;
  }

  private OwnershipAssessment assessOrLeaveUnverified(
      HeldSecurity held, LocalDate checkDate, OwnershipLimit limit) {
    try {
      return assess(held, checkDate, limit);
    } catch (Exception e) {
      log.error("Ownership assessment failed: isin={}, checkDate={}", held.isin(), checkDate, e);
      return new UnverifiedHolding(
          held.isin(), held.name(), held.value(), "assessment failed: " + describe(e));
    }
  }

  private OwnershipAssessment assess(HeldSecurity held, LocalDate checkDate, OwnershipLimit limit) {
    var isin = held.isin();
    if (isin == null) {
      return new UnverifiedHolding(null, held.name(), held.value(), "no ISIN");
    }
    var name = underlyingFunds.name(isin, held.name());
    var holdingValue = held.value();
    var leftOutBecause = LEFT_OUT_BY_DESIGN.getOrDefault(limit.fund(), Map.of()).get(isin);
    if (leftOutBecause != null) {
      return new LeftOutHolding(isin, name, holdingValue, leftOutBecause);
    }
    if (holdingValue == null) {
      return new UnverifiedHolding(isin, name, null, "no market value");
    }
    return switch (underlyingFunds.sizeInEur(isin, checkDate)) {
      case SizeInEur.Unknown unknown ->
          new UnverifiedHolding(isin, name, holdingValue, unknown.reason());
      case SizeInEur.Known size ->
          ownershipLimitChecker.check(isin, name, holdingValue, size, limit);
    };
  }

  private LimitCheckEvent event(Result result) {
    return LimitCheckEvent.builder()
        .fund(result.fund())
        .checkDate(result.checkDate())
        .checkType(OWNERSHIP)
        .breachesFound(result.worstSeverity() != OK)
        .result(
            Map.of(
                HOLDINGS,
                result.holdings(),
                "unverified",
                result.unverified(),
                "leftOut",
                result.leftOut(),
                STALE_SIZES,
                result.staleSizes(),
                SIZES_COMPARED_WITH_THE_MONTH_BEFORE,
                result.sizesWereComparedWithTheMonthBefore(),
                COVERED_EVERY_HOLDING,
                result.coveredEveryHolding()))
        .build();
  }
}
