package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

record BriefedBatch(
    Instant after,
    Instant until,
    List<OutgoingPayment> payments,
    List<OutgoingPayment> carriedOver) {

  boolean isEmpty() {
    return listed().isEmpty();
  }

  List<OutgoingPayment> listed() {
    return Stream.concat(payments.stream(), carriedOver.stream()).toList();
  }

  List<OutgoingPayment> withStatus(OutgoingPaymentStatus status) {
    return ofStatus(listed(), status);
  }

  List<OutgoingPayment> sentSinceThePreviousBriefWithStatus(OutgoingPaymentStatus status) {
    return ofStatus(payments, status);
  }

  List<OutgoingPayment> carriedOverWithStatus(OutgoingPaymentStatus status) {
    return ofStatus(carriedOver, status);
  }

  List<OutgoingPayment> carriedOverAwaitingApproval() {
    return carriedOver.stream().filter(OutgoingPayment::isAwaitingApproval).toList();
  }

  List<OutgoingPayment> sent() {
    return listed().stream().filter(payment -> payment.getStatus() != FAILED).toList();
  }

  List<OutgoingPayment> sentSinceThePreviousBrief() {
    return payments.stream().filter(payment -> payment.getStatus() != FAILED).toList();
  }

  private static List<OutgoingPayment> ofStatus(
      List<OutgoingPayment> payments, OutgoingPaymentStatus status) {
    return payments.stream().filter(payment -> payment.getStatus() == status).toList();
  }
}
