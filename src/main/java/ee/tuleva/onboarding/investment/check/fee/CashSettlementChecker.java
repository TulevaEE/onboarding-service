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

import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
    var payments = paymentsSinceSettlement(fund, settlementDate, windowCloses);

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
              List.of("multiplePaymentsInWindow=" + payments.size()),
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
        return List.of(finding(fund, PASS, List.of(), "", null, details));
      }
      if (checkDate.isBefore(windowCloses)) {
        return List.of(
            finding(
                fund,
                NOT_RUN,
                List.of("paymentWindowStillOpen"),
                "No fee payment observed for " + feeMonth + " yet, window closes " + windowCloses,
                null,
                details));
      }
      return List.of(
          finding(
              fund,
              WARNING,
              List.of("noPaymentObserved"),
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
              List.of("paymentDiffersFromSettlement"),
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
    return List.of(finding(fund, PASS, List.of(), "", null, details));
  }

  private String amounts(List<LedgerEntryAmount> payments) {
    return payments.stream()
        .map(payment -> payment.amount().toPlainString())
        .collect(joining(", "));
  }

  private FeeCheckFinding finding(
      TulevaFund fund,
      FeeCheckSeverity severity,
      List<String> identifiers,
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
        identifiers,
        details);
  }

  private List<LedgerEntryAmount> paymentsSinceSettlement(
      TulevaFund fund, LocalDate settlementDate, LocalDate windowCloses) {
    return entries(
        SystemAccount.MANAGEMENT_FEE.getAccountName(fund),
        MANAGEMENT_FEE_PAYMENT,
        startOf(settlementDate),
        startOf(windowCloses));
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
