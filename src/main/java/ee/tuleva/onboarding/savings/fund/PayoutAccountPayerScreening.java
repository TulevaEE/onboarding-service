package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening.NOT_CHECKED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PayoutAccountPayerScreening {

  private final BankAccounts bankAccounts;
  private final PaymentPartyScreener paymentPartyScreener;

  Screening screen(SavingFundPayment payment) {
    if (!receivedOnPayoutAccount(payment)) {
      return NOT_CHECKED;
    }
    var remitterName =
        requireNonNull(
            payment.getRemitterName(),
            "Payment without remitter name: paymentId=" + payment.getId());
    return paymentPartyScreener.screenPayer(payment.getRemitterIdCode(), remitterName);
  }

  private boolean receivedOnPayoutAccount(SavingFundPayment payment) {
    return bankAccounts
        .find(payment.getBeneficiaryIban())
        .filter(account -> account.matches(TKF100, WITHDRAWAL_EUR))
        .isPresent();
  }
}
