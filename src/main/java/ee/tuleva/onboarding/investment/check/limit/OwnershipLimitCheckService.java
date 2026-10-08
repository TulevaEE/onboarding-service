package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.CheckType.OWNERSHIP;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.investment.check.limit.HeldSecurities.HeldSecurity;
import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.NotChecked;
import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.Result;
import ee.tuleva.onboarding.investment.check.limit.UnderlyingFunds.SizeInEur;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
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
  private static final String HOLDINGS = "holdings";

  // Left out of the check on purpose, and listed in its message instead of being sized.
  // IE00BFG1TM61, iShares Developed World Screened Index Fund: EODHD has no total assets for it, so
  // it could only ever be "not verified", which made every month INCOMPLETE and re-ran the check
  // each morning to the 14th. The fund is so large that TKF100 will never own 25% of it.
  private static final Map<String, String> LEFT_OUT_BY_DESIGN =
      Map.of(
          "IE00BFG1TM61",
          "EODHD has no total assets, and the fund is too large for TKF100 to own 25% of it");

  private final LimitCheckEventWriter limitCheckEventWriter;
  private final LimitCheckEventRepository limitCheckEventRepository;

  boolean everyFundIsChecked(YearMonth month) {
    var limits = ownershipLimitProvider.findAllLatestAsOf(month.atEndOfMonth());
    return !limits.isEmpty()
        && limits.stream().allMatch(limit -> everyHoldingWasSized(limit.fund(), month));
  }

  private boolean everyHoldingWasSized(TulevaFund fund, YearMonth month) {
    return limitCheckEventRepository
        .findByFundAndCheckTypeAndCheckDateBetween(
            fund, OWNERSHIP, month.atDay(1), month.atEndOfMonth())
        .stream()
        .anyMatch(event -> Boolean.TRUE.equals(event.getResult().get(COVERED_EVERY_HOLDING)));
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
            staleSizes(fund, checkDate, holdings));
    limitCheckEventWriter.replaceEvents(fund, checkDate, List.of(event(result)));
    return result;
  }

  // EODHD's total assets for some funds stay the same for months while its update date moves on
  // (seen 08.10.2026 for three of TKF100's holdings), so the freshness check cannot catch them. A
  // real fund's assets change every month, so a figure identical to the one stored the month
  // before is flagged as probably stale. The holding is still checked against it.
  private List<StaleFundSize> staleSizes(
      TulevaFund fund, LocalDate checkDate, List<OwnershipBreach> holdings) {
    var monthBefore = YearMonth.from(checkDate).minusMonths(1);
    return limitCheckEventRepository
        .findByFundAndCheckTypeAndCheckDateBetween(
            fund, OWNERSHIP, monthBefore.atDay(1), monthBefore.atEndOfMonth())
        .stream()
        .max(comparing(LimitCheckEvent::getCheckDate))
        .map(
            previous -> {
              var previousSizes = reportedFundSizes(previous);
              return holdings.stream()
                  .filter(
                      holding ->
                          Optional.ofNullable(previousSizes.get(holding.isin()))
                              .filter(size -> size.compareTo(holding.reportedFundSize()) == 0)
                              .isPresent())
                  .map(
                      holding ->
                          new StaleFundSize(
                              holding.isin(),
                              holding.name(),
                              holding.reportedFundSize(),
                              holding.reportedCurrency(),
                              previous.getCheckDate()))
                  .toList();
            })
        .orElse(List.of());
  }

  private static Map<String, BigDecimal> reportedFundSizes(LimitCheckEvent event) {
    var sizes = new HashMap<String, BigDecimal>();
    if (event.getResult().get(HOLDINGS) instanceof List<?> holdings) {
      for (var holding : holdings) {
        if (holding instanceof Map<?, ?> stored
            && stored.get("isin") instanceof String isin
            && stored.get("reportedFundSize") instanceof Number size) {
          sizes.put(isin, new BigDecimal(size.toString()));
        }
      }
    }
    return sizes;
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
    var leftOutBecause = LEFT_OUT_BY_DESIGN.get(isin);
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
                "staleSizes",
                result.staleSizes(),
                COVERED_EVERY_HOLDING,
                result.coveredEveryHolding()))
        .build();
  }
}
