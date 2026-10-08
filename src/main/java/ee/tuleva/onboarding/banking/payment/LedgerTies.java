package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.REDEMPTION_TRANSFER;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.RETURN;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.SUBSCRIPTION_TRANSFER;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBrief.amount;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LedgerTies {
  private final LedgerExpectations ledger;

  List<LedgerTie> of(BriefedBatch batch) {
    var sent = batch.sent();
    return Stream.of(
            each(sent, PAYOUT, "payouts == ledger pricing", ledger::pricedRedemption),
            each(
                sent,
                REDEMPTION_TRANSFER,
                "transfer to withdrawal account == ledger pricing of its batch",
                ledger::pricedRedemptionBatch),
            subscriptions(batch, sent),
            each(sent, RETURN, "returns == ledger return booking", ledger::bookedReturn))
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<LedgerTie> each(
      List<OutgoingPayment> sent,
      OutgoingPaymentType type,
      String label,
      Function<UUID, Optional<BigDecimal>> expected) {
    var ofType = ofType(sent, type);
    if (ofType.isEmpty()) {
      return Optional.empty();
    }
    var mismatches =
        ofType.stream()
            .flatMap(payment -> mismatch(payment, expectedFor(payment, expected)).stream())
            .toList();
    return Optional.of(new LedgerTie(label, mismatches));
  }

  private static Optional<BigDecimal> expectedFor(
      OutgoingPayment payment, Function<UUID, Optional<BigDecimal>> expected) {
    var sourceId = payment.getSourceId();
    return sourceId == null ? Optional.empty() : expected.apply(sourceId);
  }

  private static Optional<String> mismatch(OutgoingPayment payment, Optional<BigDecimal> expected) {
    if (expected.isPresent() && expected.get().compareTo(payment.getAmount()) == 0) {
      return Optional.empty();
    }
    return Optional.of(
        "%s sent %s EUR, ledger %s"
            .formatted(
                payment.getEndToEndId(),
                amount(payment.getAmount()),
                expected.map(value -> amount(value) + " EUR").orElse("has none")));
  }

  private Optional<LedgerTie> subscriptions(BriefedBatch batch, List<OutgoingPayment> sent) {
    var transferred = sum(ofType(sent, SUBSCRIPTION_TRANSFER));
    var issued = ledger.issuedSubscriptions(batch.after(), batch.until());
    if (transferred.signum() == 0 && issued.signum() == 0) {
      return Optional.empty();
    }
    var mismatches =
        transferred.compareTo(issued) == 0
            ? List.<String>of()
            : List.of(
                "transferred %s EUR, ledger issued %s EUR"
                    .formatted(amount(transferred), amount(issued)));
    return Optional.of(
        new LedgerTie(
            "transfer to fund account == subscriptions issued in the ledger", mismatches));
  }

  private static List<OutgoingPayment> ofType(
      List<OutgoingPayment> payments, OutgoingPaymentType type) {
    return payments.stream().filter(payment -> payment.getPaymentType() == type).toList();
  }

  private static BigDecimal sum(List<OutgoingPayment> payments) {
    return payments.stream().map(OutgoingPayment::getAmount).reduce(ZERO, BigDecimal::add);
  }
}
