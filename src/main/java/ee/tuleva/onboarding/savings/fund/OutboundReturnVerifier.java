package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RETURNED;

import ee.tuleva.onboarding.banking.payment.OutgoingPaymentLookup;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OutboundReturnVerifier {

  private static final Set<OutgoingPaymentStatus> LIVE_AT_THE_BANK = Set.of(ATTEMPTED, SUBMITTED);

  private final OutgoingPaymentLookup outgoingPaymentLookup;

  void verifyAttributable(SavingFundPayment payment, boolean returnCancelled) {
    var paymentId = payment.getId();
    var returnOrderStatus = outgoingPaymentLookup.findStatusForSource(paymentId).orElse(null);

    if (returnOrderStatus == EXECUTED) {
      throw new IllegalStateException(
          "The bank has already executed the return, the money has left the account: paymentId="
              + paymentId);
    }
    if (needsCancellationAttestation(payment, returnOrderStatus) && !returnCancelled) {
      throw new IllegalStateException(
          "Outbound return may still be in flight; cancel the pending bank return first, then retry"
              + " with returnCancelled=true: paymentId="
              + paymentId
              + ", returnOrderStatus="
              + returnOrderStatus);
    }
  }

  private boolean needsCancellationAttestation(
      SavingFundPayment payment, @Nullable OutgoingPaymentStatus returnOrderStatus) {
    if (returnOrderStatus == null) {
      return payment.getStatus() == RETURNED;
    }
    return LIVE_AT_THE_BANK.contains(returnOrderStatus);
  }
}
