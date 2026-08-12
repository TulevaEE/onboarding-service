package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.CASH_SETTLEMENT_OBSERVED;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_PAYMENT;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Closes the loop from the accrual ledger to money actually leaving the fund account. Matching is
// deliberately coarse: the payment carries no fee month, no fee type and no reference to the
// settled accrual, and it is recognised from a beneficiary name plus a description substring. So a
// renamed payment description reads here as "settlement not observed", which is the honest result -
// the fix for that belongs in the ingestion matcher, not in this check.
@Component
class CashSettlementChecker {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final NavLedgerRepository navLedgerRepository;
  private final FeeCashIngestionCoverage coverage;
  private final BigDecimal cashPaymentTolerance;
  private final int paymentWindowDays;

  CashSettlementChecker(
      NavLedgerRepository navLedgerRepository,
      FeeCashIngestionCoverage coverage,
      @Value("${investment.fee-check.cash-payment-tolerance:0.02}") BigDecimal cashPaymentTolerance,
      @Value("${investment.fee-check.cash-payment-window-days:20}") int paymentWindowDays) {
    this.navLedgerRepository = navLedgerRepository;
    this.coverage = coverage;
    this.cashPaymentTolerance = cashPaymentTolerance;
    this.paymentWindowDays = paymentWindowDays;
  }

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate feeMonth, LocalDate checkDate) {
    if (!coverage.coversFund(fund)) {
      return List.of();
    }

    var settlementDate = feeMonth.plusMonths(1).minusDays(1);
    var windowCloses = settlementDate.plusDays(paymentWindowDays);
    var settled =
        sum(
            entries(
                FeeType.MANAGEMENT.getAccrualAccount().getAccountName(fund),
                FEE_SETTLEMENT,
                startOf(feeMonth),
                startOf(feeMonth.plusMonths(1))));
    // The window opens on the settlement date, not on the fee month. A month is only settled on
    // its last day, so anything paid earlier belongs to the previous month - and counting from the
    // fee month would sweep that in and report two payments every single month.
    var payments =
        entries(
            SystemAccount.MANAGEMENT_FEE.getAccountName(fund),
            MANAGEMENT_FEE_PAYMENT,
            startOf(settlementDate),
            startOf(windowCloses));

    var details =
        Map.<String, Object>of(
            "feeMonth", feeMonth.toString(),
            "settled", settled.toPlainString(),
            "windowCloses", windowCloses.toString(),
            "payments", payments.stream().map(p -> p.amount().toPlainString()).toList());

    if (payments.size() > 1) {
      return List.of(
          finding(
              fund,
              WARNING,
              "Found "
                  + payments.size()
                  + " management fee payments in the window for "
                  + feeMonth
                  + " ("
                  + amounts(payments)
                  + "), which cannot be attributed to a fee month",
              null,
              details));
    }

    if (payments.isEmpty()) {
      if (settled.signum() == 0) {
        return List.of(finding(fund, PASS, "", null, details));
      }
      if (checkDate.isBefore(windowCloses)) {
        return List.of(
            finding(
                fund,
                NOT_RUN,
                "No fee payment observed for " + feeMonth + " yet, window closes " + windowCloses,
                null,
                details));
      }
      return List.of(
          finding(
              fund,
              WARNING,
              "Settled "
                  + settled.toPlainString()
                  + " of management fees for "
                  + feeMonth
                  + " but no payment was observed by "
                  + windowCloses,
              settled,
              details));
    }

    var paid = payments.getFirst().amount();
    var deviation = paid.subtract(settled);
    if (deviation.abs().compareTo(cashPaymentTolerance) > 0) {
      return List.of(
          finding(
              fund,
              WARNING,
              "Management fees paid for "
                  + feeMonth
                  + " were "
                  + paid.toPlainString()
                  + " but "
                  + settled.toPlainString()
                  + " was settled",
              deviation,
              details));
    }
    return List.of(finding(fund, PASS, "", null, details));
  }

  private String amounts(List<LedgerEntryAmount> payments) {
    return payments.stream()
        .map(payment -> payment.amount().toPlainString())
        .collect(joining(", "));
  }

  private FeeCheckFinding finding(
      TulevaFund fund,
      FeeCheckSeverity severity,
      String message,
      @Nullable BigDecimal deviation,
      Map<String, Object> details) {
    return new FeeCheckFinding(
        fund,
        CASH_SETTLEMENT_OBSERVED,
        MANAGEMENT,
        severity,
        message,
        deviation == null ? null : deviation.abs(),
        details);
  }

  private List<LedgerEntryAmount> entries(
      String account, TransactionType transactionType, Instant from, Instant to) {
    return navLedgerRepository.findEntriesByTransactionTypeBetween(
        account, transactionType, from, to);
  }

  private BigDecimal sum(List<LedgerEntryAmount> entries) {
    return entries.stream().map(LedgerEntryAmount::amount).reduce(ZERO, BigDecimal::add);
  }

  private Instant startOf(LocalDate month) {
    return month.atStartOfDay(ESTONIAN_ZONE).toInstant();
  }
}
