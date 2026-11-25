package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;

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
  private final PaymentPartyScreener paymentPartyScreener;

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
                "A return order already exists at the bank: returnOrderStatus=" + returnOrderStatus)
        .or(() -> payoutAccountPayerScreeningBlock(payment, remitterName));
  }

  private Optional<String> payoutAccountPayerScreeningBlock(
      SavingFundPayment payment, String remitterName) {
    if (!receivedOnPayoutAccount(payment)) {
      return Optional.empty();
    }
    return switch (paymentPartyScreener.screenPayer(payment.getRemitterIdCode(), remitterName)) {
      case SANCTION_HIT -> Optional.of("Payer has a sanctions hit, the money stays on our account");
      case UNAVAILABLE -> Optional.of("Payer sanctions screening is unavailable, try again later");
      case CLEAR, PEP_HIT, NOT_CHECKED -> Optional.empty();
    };
  }

  private boolean receivedOnPayoutAccount(SavingFundPayment payment) {
    return bankAccounts
        .find(payment.getBeneficiaryIban())
        .filter(account -> account.matches(TKF100, WITHDRAWAL_EUR))
        .isPresent();
  }
}
