package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.FEE_POLICY_UNRESOLVED;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeePolicyUnresolvedException;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;

@Slf4j
@NullMarked
@RequiredArgsConstructor
final class AccruedFeeReader {

  private final FeeAccrualRepository feeAccrualRepository;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;

  BigDecimal accruedFees(
      TulevaFund fund,
      LocalDate asOfDate,
      FeeType feeType,
      List<CalculationWarning> inputWarnings) {
    LocalDate feeMonth = asOfDate.withDayOfMonth(1);
    Map<LocalDate, BigDecimal> accrualsByDate =
        feeAccrualRepository.getAccruedFeesByDateForMonth(
            fund, feeMonth, List.of(feeType), asOfDate);
    FeeChargedToFundPolicy.Resolver resolver;
    try {
      resolver = feeChargedToFundPolicy.resolverFor(fund, feeType);
    } catch (FeePolicyUnresolvedException e) {
      log.warn("Fee policy does not resolve, reserving every accrual day", e);
      inputWarnings.add(
          unresolvedFeePolicyWarning(
              fund, asOfDate, feeType, accrualsByDate.size(), e.getReason()));
      return sumOf(accrualsByDate.values());
    }
    return sumReservingUnresolvedDays(
        resolver, accrualsByDate, fund, asOfDate, feeType, inputWarnings);
  }

  private BigDecimal sumReservingUnresolvedDays(
      FeeChargedToFundPolicy.Resolver resolver,
      Map<LocalDate, BigDecimal> accrualsByDate,
      TulevaFund fund,
      LocalDate asOfDate,
      FeeType feeType,
      List<CalculationWarning> inputWarnings) {
    Map<LocalDate, String> unresolvedReasons = unresolvedReasons(resolver, accrualsByDate.keySet());
    if (!unresolvedReasons.isEmpty()) {
      inputWarnings.add(
          unresolvedFeePolicyWarning(
              fund,
              asOfDate,
              feeType,
              unresolvedReasons.size(),
              unresolvedReasons.values().iterator().next()));
    }
    return sumOf(
        accrualsByDate.entrySet().stream()
            .filter(
                entry ->
                    unresolvedReasons.containsKey(entry.getKey())
                        || resolver.chargedOn(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList());
  }

  private Map<LocalDate, String> unresolvedReasons(
      FeeChargedToFundPolicy.Resolver resolver, Set<LocalDate> accrualDates) {
    Map<LocalDate, String> reasons = new LinkedHashMap<>();
    for (LocalDate date : accrualDates) {
      try {
        resolver.chargedOn(date);
      } catch (FeePolicyUnresolvedException e) {
        log.warn("Fee policy does not resolve for one accrual day, reserving it", e);
        reasons.put(date, e.getReason());
      }
    }
    return reasons;
  }

  private CalculationWarning unresolvedFeePolicyWarning(
      TulevaFund fund, LocalDate asOfDate, FeeType feeType, int unresolvedDays, String reason) {
    String message =
        ("Fee policy does not resolve, reserving the unresolved days as if they were charged to the"
                + " fund: fund=%s, feeType=%s, asOfDate=%s, unresolvedDays=%d, reason=%s")
            .formatted(fund.name(), feeType, asOfDate, unresolvedDays, reason);
    log.warn(message);
    return new CalculationWarning(FEE_POLICY_UNRESOLVED, message);
  }

  private BigDecimal sumOf(Collection<BigDecimal> amounts) {
    return amounts.stream().reduce(ZERO, BigDecimal::add);
  }
}
