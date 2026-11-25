package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.savings.SavingFundPaymentFixture.aPayment;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening.NOT_CHECKED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening.SANCTION_HIT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayoutAccountPayerScreeningTest {

  private static final String PAYOUT_ACCOUNT_IBAN = "EE662200221066655544";
  private static final String DEPOSIT_ACCOUNT_IBAN = "EE442200221092874625";

  @Mock BankAccounts bankAccounts;
  @Mock PaymentPartyScreener paymentPartyScreener;

  @InjectMocks PayoutAccountPayerScreening screening;

  @Test
  void screensThePayerOfAPaymentThatLandedOnThePayoutAccount() {
    given(bankAccounts.find(PAYOUT_ACCOUNT_IBAN))
        .willReturn(
            Optional.of(new BankAccount(PAYOUT_ACCOUNT_IBAN, WITHDRAWAL_EUR, TKF100, "gw-test")));
    given(paymentPartyScreener.screenPayer("14118923", "Some Company OÜ")).willReturn(SANCTION_HIT);
    var payment =
        aPayment()
            .beneficiaryIban(PAYOUT_ACCOUNT_IBAN)
            .remitterIdCode("14118923")
            .remitterName("Some Company OÜ")
            .build();

    assertThat(screening.screen(payment)).isEqualTo(SANCTION_HIT);
  }

  @Test
  void leavesDepositAccountReturnsToTheDepositFlowsOwnScreening() {
    given(bankAccounts.find(DEPOSIT_ACCOUNT_IBAN))
        .willReturn(
            Optional.of(new BankAccount(DEPOSIT_ACCOUNT_IBAN, DEPOSIT_EUR, TKF100, "gw-test")));
    var payment = aPayment().beneficiaryIban(DEPOSIT_ACCOUNT_IBAN).build();

    assertThat(screening.screen(payment)).isEqualTo(NOT_CHECKED);
    verifyNoInteractions(paymentPartyScreener);
  }
}
