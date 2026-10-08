package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;

import java.time.Instant;
import java.util.List;

record BriefedBatch(
    Instant after,
    Instant until,
    List<OutgoingPayment> payments,
    List<OutgoingPayment> carriedOver) {

  boolean isEmpty() {
    return payments.isEmpty();
  }

  List<OutgoingPayment> withStatus(OutgoingPaymentStatus status) {
    return payments.stream().filter(payment -> payment.getStatus() == status).toList();
  }

  List<OutgoingPayment> sent() {
    return payments.stream().filter(payment -> payment.getStatus() != FAILED).toList();
  }
}
