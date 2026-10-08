package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.INFO;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.DEBIT_MISMATCH;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_BATCH_NOT_SETTLED;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_BATCH_SETTLED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBrief.amount;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBrief.count;
import static java.math.BigDecimal.ZERO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.banking.check.payment.PaymentCheckType;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PaymentSettlementCheck {
  private static final Duration STATEMENT_RUN_TO_FINISH = Duration.ofMinutes(2);

  private final LedgerTies ledgerTies;
  private final PaymentAccounts paymentAccounts;
  private final PaymentCheckService paymentCheckService;
  private final Clock clock;

  @Value("${banking.payment.in-flight-grace:PT15M}")
  private final Duration inFlightGrace;

  Optional<Closing> closingFor(BriefedBatch batch, LocalDate today) {
    if (batch.isEmpty()
        || paymentCheckService.hasRecorded(PAYMENT_BATCH_SETTLED, today.toString())
        || stillSettling(batch, Instant.now(clock))) {
      return Optional.empty();
    }

    var ties = ledgerTies.of(batch);
    var problems = problems(batch, ties);
    if (problems.isEmpty()) {
      return Optional.of(
          new Closing(PAYMENT_BATCH_SETTLED, today.toString(), settled(batch, ties)));
    }

    var key = today + ":" + digestOf(problems);
    if (paymentCheckService.hasRecorded(PAYMENT_BATCH_NOT_SETTLED, key)) {
      return Optional.empty();
    }
    return Optional.of(new Closing(PAYMENT_BATCH_NOT_SETTLED, key, notSettled(problems)));
  }

  void markPosted(Closing closing) {
    paymentCheckService.record(closing.outcome(), INFO, closing.key(), closing.headline());
  }

  private boolean stillSettling(BriefedBatch batch, Instant now) {
    var awaitingApproval = !batch.withStatus(SUBMITTED).isEmpty();
    var stillInFlight =
        batch.withStatus(ATTEMPTED).stream()
            .anyMatch(payment -> payment.getAttemptedAt().isAfter(now.minus(inFlightGrace)));
    var statementRunStillBooking =
        batch.payments().stream()
            .map(OutgoingPayment::getResolvedAt)
            .anyMatch(
                resolvedAt ->
                    resolvedAt != null && resolvedAt.isAfter(now.minus(STATEMENT_RUN_TO_FINISH)));
    return awaitingApproval || stillInFlight || statementRunStillBooking;
  }

  private List<String> problems(BriefedBatch batch, List<LedgerTie> ties) {
    var problems = new ArrayList<String>();
    problem("from earlier briefs still not executed", batch.carriedOver()).ifPresent(problems::add);
    problem("never got an answer from the bank", batch.withStatus(ATTEMPTED))
        .ifPresent(problems::add);
    problem("rejected by the bank at submission", batch.withStatus(FAILED))
        .ifPresent(problems::add);
    problem("not confirmed to match what we sent (DEBIT_MISMATCH)", unconfirmedDebits(batch))
        .ifPresent(problems::add);
    ties.stream().filter(tie -> !tie.holds()).map(LedgerTie::problem).forEach(problems::add);
    return problems;
  }

  private List<OutgoingPayment> unconfirmedDebits(BriefedBatch batch) {
    return batch.withStatus(EXECUTED).stream()
        .filter(payment -> paymentCheckService.hasRecorded(DEBIT_MISMATCH, payment.getEndToEndId()))
        .toList();
  }

  private static Optional<String> problem(String what, List<OutgoingPayment> payments) {
    if (payments.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        "%s %s: %s"
            .formatted(
                count(payments.size()),
                what,
                payments.stream().map(OutgoingPayment::getEndToEndId).collect(joining(", "))));
  }

  private String settled(BriefedBatch batch, List<LedgerTie> ties) {
    var payments = batch.payments();
    var total = payments.stream().map(OutgoingPayment::getAmount).reduce(ZERO, BigDecimal::add);
    var accounts =
        paymentAccounts.group(payments).stream()
            .map(AccountPayments::summary)
            .collect(joining("\n"));
    var checks =
        ties.stream().map(tie -> "      ✅ " + tie.label()).collect(joining("\n", "\n", ""));
    return """
        ✅ TKF100 — all %s of the approval brief executed as sent and as the ledger expects

        %s

          TOTAL  %s  %s EUR

          Checks:
              ✅ each debit matches the amount and account we sent%s"""
        .formatted(count(payments.size()), accounts, count(payments.size()), amount(total), checks);
  }

  private static String notSettled(List<String> problems) {
    return "🔴 TKF100 — payments of the approval brief are not all in order\n\n"
        + problems.stream().map(problem -> "  ❌ " + problem).collect(joining("\n"));
  }

  private static String digestOf(List<String> problems) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(String.join("\n", problems).getBytes(UTF_8)))
          .substring(0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  record Closing(PaymentCheckType outcome, String key, String message) {
    String headline() {
      return message.lines().findFirst().orElseThrow();
    }
  }
}
