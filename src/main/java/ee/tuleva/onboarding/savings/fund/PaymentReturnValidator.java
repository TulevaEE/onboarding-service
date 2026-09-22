package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentLookup;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PaymentReturnValidator {

  private final BankAccounts bankAccounts;
  private final OutgoingPaymentLookup outgoingPaymentLookup;

  Optional<String> findBlockingReason(SavingFundPayment payment) {
    if (payment.getStatus() != TO_BE_RETURNED) {
      return Optional.of("Payment is not awaiting return: status=" + payment.getStatus());
    }
    var remitterIban = payment.getRemitterIban();
    if (remitterIban == null || remitterIban.isBlank()) {
      return Optional.of("Payment has no remitter IBAN to return the money to");
    }
    if (bankAccounts.find(remitterIban).isPresent()) {
      return Optional.of("Return would go to one of our own accounts: iban=" + remitterIban);
    }
    var remitterName = payment.getRemitterName();
    if (remitterName == null || remitterName.isBlank()) {
      return Optional.of("Payment has no remitter name to return the money to");
    }
    if (payment.getAmount().signum() <= 0) {
      return Optional.of("Return amount is not positive: amount=" + payment.getAmount());
    }
    return outgoingPaymentLookup
        .findStatusForSource(payment.getId())
        .filter(returnOrderStatus -> returnOrderStatus != FAILED)
        .map(
            returnOrderStatus ->
                "A return order already exists at the bank: returnOrderStatus="
                    + returnOrderStatus);
  }
}
