package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.REDEMPTION_TRANSFER;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BatchTies {
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final OutgoingPaymentRepository outgoingPaymentRepository;

  List<PaymentApprovalBrief.Verdict> verdicts(
      List<OutgoingPayment> awaitingApproval, LocalDate date) {
    var inScope = tieScope(awaitingApproval, date);
    var verdicts = new ArrayList<>(ties(inScope));
    retriedPayouts(inScope).ifPresent(verdicts::add);
    return List.copyOf(verdicts);
  }

  private List<OutgoingPayment> tieScope(List<OutgoingPayment> awaitingApproval, LocalDate date) {
    var onTheBrief = live(Stream.concat(awaitingApproval.stream(), attemptedOn(date).stream()));
    var batchIds =
        onTheBrief.stream()
            .flatMap(payment -> Stream.ofNullable(payment.getBatchId()))
            .collect(toSet());
    var batched =
        batchIds.isEmpty()
            ? List.<OutgoingPayment>of()
            : live(outgoingPaymentRepository.findByBatchIdIn(batchIds).stream());
    var retried =
        onTheBrief.stream()
            .filter(payment -> payment.getBatchId() == null)
            .filter(payment -> payment.getPaymentType() == PAYOUT)
            .toList();
    return Stream.concat(batched.stream(), retried.stream()).toList();
  }

  private List<OutgoingPayment> attemptedOn(LocalDate date) {
    return outgoingPaymentRepository.findByAttemptedAtBetween(
        date.atStartOfDay(TALLINN).toInstant(), date.plusDays(1).atStartOfDay(TALLINN).toInstant());
  }

  private static List<OutgoingPayment> live(Stream<OutgoingPayment> payments) {
    return payments
        .filter(payment -> payment.getStatus() != FAILED)
        .collect(
            toMap(
                OutgoingPayment::getEndToEndId,
                identity(),
                (first, second) -> first,
                LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  private static List<PaymentApprovalBrief.Verdict> ties(List<OutgoingPayment> inScope) {
    var byBatch =
        inScope.stream()
            .filter(payment -> payment.getBatchId() != null)
            .collect(groupingBy(payment -> requireNonNull(payment.getBatchId())));
    return byBatch.entrySet().stream()
        .sorted(
            comparing(
                    (Map.Entry<UUID, List<OutgoingPayment>> batch) ->
                        earliestAttempt(batch.getValue()))
                .thenComparing(batch -> batch.getKey().toString()))
        .map(batch -> tie(batch.getKey(), batch.getValue()))
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<PaymentApprovalBrief.Verdict> tie(
      UUID batchId, List<OutgoingPayment> batch) {
    var transferred = totalOf(batch, REDEMPTION_TRANSFER);
    var paidOut = totalOf(batch, PAYOUT);
    if (transferred.signum() == 0 && paidOut.signum() == 0) {
      return Optional.empty();
    }
    return Optional.of(
        new PaymentApprovalBrief.Verdict(
            "payouts == transfer to withdrawal account (batch %s)".formatted(marker(batchId)),
            transferred.compareTo(paidOut) == 0,
            "%s = %s"
                .formatted(
                    PaymentApprovalBrief.amount(paidOut),
                    PaymentApprovalBrief.amount(transferred))));
  }

  private static Optional<PaymentApprovalBrief.Verdict> retriedPayouts(
      List<OutgoingPayment> inScope) {
    var retried = inScope.stream().filter(payment -> payment.getBatchId() == null).toList();
    if (retried.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new PaymentApprovalBrief.Verdict(
            "retried payouts, outside any batch",
            true,
            "%s, %s"
                .formatted(
                    PaymentApprovalBrief.count(retried.size()),
                    PaymentApprovalBrief.amount(sum(retried)))));
  }

  private static String marker(UUID batchId) {
    return batchId.toString().substring(0, 8);
  }

  private static Instant earliestAttempt(List<OutgoingPayment> batch) {
    return batch.stream().map(OutgoingPayment::getAttemptedAt).min(naturalOrder()).orElseThrow();
  }

  private static BigDecimal totalOf(List<OutgoingPayment> payments, OutgoingPaymentType type) {
    return sum(payments.stream().filter(payment -> payment.getPaymentType() == type).toList());
  }

  private static BigDecimal sum(List<OutgoingPayment> payments) {
    return payments.stream().map(OutgoingPayment::getAmount).reduce(ZERO, BigDecimal::add);
  }
}
