package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.LEDGER_ACCRUAL_CONSISTENCY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_ACCRUAL;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.DailyAccrualAmount;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LedgerAccrualConsistencyChecker {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final int MAX_DAYS_IN_MESSAGE = 10;

  private final FeeAccrualRepository feeAccrualRepository;
  private final NavLedgerRepository navLedgerRepository;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;

  List<FeeCheckFinding> check(TulevaFund fund, FeeType feeType, LocalDate from, LocalDate to) {
    var accrualsByDate = accrualsByDate(fund, feeType, from, to);
    var entries = ledgerEntries(fund, feeType, from, to);
    var ledgerByDate = ledgerAmountsByDate(entries);
    var transactionCountByDate = transactionCountsByDate(entries);
    var charged = feeChargedToFundPolicy.resolverFor(fund, feeType);

    var dates = new TreeSet<>(accrualsByDate.keySet());
    dates.addAll(ledgerByDate.keySet());

    var divergences =
        dates.stream()
            .map(
                date ->
                    divergenceOn(
                        date,
                        accrualsByDate.get(date),
                        charged.chargedOn(date),
                        ledgerByDate.getOrDefault(date, ZERO),
                        transactionCountByDate.getOrDefault(date, 0L)))
            .filter(Divergence::isDivergent)
            .toList();

    if (divergences.isEmpty()) {
      return List.of(FeeCheckFinding.pass(fund, LEDGER_ACCRUAL_CONSISTENCY, scopeOf(feeType)));
    }
    return List.of(failure(fund, feeType, divergences));
  }

  private Divergence divergenceOn(
      LocalDate date,
      @Nullable BigDecimal accrual,
      boolean chargedToFund,
      BigDecimal ledgerAmount,
      long transactionCount) {
    var expectedLedgerAmount = accrual == null || !chargedToFund ? ZERO : accrual.negate();
    var expectedTransactionCount = expectedLedgerAmount.signum() == 0 ? 0 : 1;
    return new Divergence(
        date,
        accrual,
        expectedLedgerAmount,
        ledgerAmount,
        transactionCount,
        expectedTransactionCount);
  }

  private FeeCheckFinding failure(TulevaFund fund, FeeType feeType, List<Divergence> divergences) {
    var total =
        divergences.stream().map(Divergence::difference).reduce(ZERO, BigDecimal::add).abs();
    return new FeeCheckFinding(
        fund,
        LEDGER_ACCRUAL_CONSISTENCY,
        scopeOf(feeType),
        FeeCheckSeverity.FAIL,
        message(feeType, divergences, total),
        total,
        Map.of(
            "divergentDays",
            divergences.stream().map(Divergence::describe).toList(),
            "totalDeviation",
            total.toPlainString()));
  }

  private String message(FeeType feeType, List<Divergence> divergences, BigDecimal total) {
    var shown = divergences.stream().limit(MAX_DAYS_IN_MESSAGE).map(Divergence::describe).toList();
    var suffix =
        divergences.size() > MAX_DAYS_IN_MESSAGE
            ? " ... (" + (divergences.size() - MAX_DAYS_IN_MESSAGE) + " more)"
            : "";
    return feeType.name()
        + " accrual table and ledger disagree on "
        + divergences.size()
        + " day(s), total "
        + total.toPlainString()
        + ": "
        + String.join(" · ", shown)
        + suffix;
  }

  private Map<LocalDate, BigDecimal> accrualsByDate(
      TulevaFund fund, FeeType feeType, LocalDate from, LocalDate to) {
    return feeAccrualRepository.findRoundedDailyGrossBetween(fund, feeType, from, to).stream()
        .collect(
            groupingBy(
                DailyAccrualAmount::date,
                reducing(ZERO, DailyAccrualAmount::amount, BigDecimal::add)));
  }

  private List<LedgerEntryAmount> ledgerEntries(
      TulevaFund fund, FeeType feeType, LocalDate from, LocalDate to) {
    return navLedgerRepository.findEntriesByTransactionTypeBetween(
        accountOf(feeType).getAccountName(fund),
        FEE_ACCRUAL,
        from.atStartOfDay(ESTONIAN_ZONE).toInstant(),
        to.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant());
  }

  private Map<LocalDate, BigDecimal> ledgerAmountsByDate(List<LedgerEntryAmount> entries) {
    return entries.stream()
        .collect(
            groupingBy(this::dateOf, reducing(ZERO, LedgerEntryAmount::amount, BigDecimal::add)));
  }

  private Map<LocalDate, Long> transactionCountsByDate(List<LedgerEntryAmount> entries) {
    return entries.stream()
        .collect(groupingBy(this::dateOf, mapping(LedgerEntryAmount::transactionId, toSet())))
        .entrySet()
        .stream()
        .collect(toMap(Map.Entry::getKey, e -> (long) e.getValue().size()));
  }

  private LocalDate dateOf(LedgerEntryAmount entry) {
    return entry.transactionDate().atZone(ESTONIAN_ZONE).toLocalDate();
  }

  private SystemAccount accountOf(FeeType feeType) {
    return feeType == FeeType.MANAGEMENT
        ? SystemAccount.MANAGEMENT_FEE_ACCRUAL
        : SystemAccount.DEPOT_FEE_ACCRUAL;
  }

  private FeeCheckScope scopeOf(FeeType feeType) {
    return feeType == FeeType.MANAGEMENT ? FeeCheckScope.MANAGEMENT : FeeCheckScope.DEPOT;
  }

  private record Divergence(
      LocalDate date,
      @Nullable BigDecimal accrual,
      BigDecimal expectedLedgerAmount,
      BigDecimal ledgerAmount,
      long transactionCount,
      int expectedTransactionCount) {

    boolean isDivergent() {
      return expectedLedgerAmount.compareTo(ledgerAmount) != 0
          || transactionCount != expectedTransactionCount;
    }

    BigDecimal difference() {
      return expectedLedgerAmount.subtract(ledgerAmount);
    }

    String describe() {
      return date
          + " table="
          + (accrual == null ? "none" : accrual.toPlainString())
          + " ledger="
          + ledgerAmount.negate().toPlainString()
          + (transactionCount != expectedTransactionCount
              ? " entries=" + transactionCount + " expected=" + expectedTransactionCount
              : "");
    }
  }
}
