package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPaymentFixture.aPayment;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentLookup;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingFundPayment.Status;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentReturnValidatorTest {

  private static final String REMITTER_IBAN = "EE123456789012345678";

  @Mock private BankAccounts bankAccounts;
  @Mock private OutgoingPaymentLookup outgoingPaymentLookup;

  @InjectMocks private PaymentReturnValidator validator;

  @BeforeEach
  void allowByDefault() {
    lenient().when(bankAccounts.find(any())).thenReturn(Optional.empty());
    lenient().when(outgoingPaymentLookup.findStatusForSource(any())).thenReturn(Optional.empty());
  }

  @Test
  void aReturnablePaymentHasNoBlockingReason() {
    assertThat(validator.findBlockingReason(returnablePayment().build())).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"TO_BE_RETURNED"},
      mode = EnumSource.Mode.EXCLUDE)
  void blocksAPaymentThatIsNotAwaitingReturn(Status status) {
    var payment = returnablePayment().status(status).build();

    assertThat(validator.findBlockingReason(payment))
        .contains("Payment is not awaiting return: status=" + status);
  }

  @Test
  void blocksAPaymentWithoutARemitterIbanToReturnTheMoneyTo() {
    var payment = returnablePayment().remitterIban(null).build();

    assertThat(validator.findBlockingReason(payment))
        .contains("Payment has no remitter IBAN to return the money to");
  }

  @Test
  void blocksAReturnThatWouldGoBackToOneOfOurOwnAccounts() {
    given(bankAccounts.find(REMITTER_IBAN))
        .willReturn(Optional.of(new BankAccount(REMITTER_IBAN, DEPOSIT_EUR, TKF100, "gw-test")));

    assertThat(validator.findBlockingReason(returnablePayment().build()))
        .contains("Return would go to one of our own accounts: iban=" + REMITTER_IBAN);
  }

  @Test
  void blocksAPaymentWithoutARemitterNameToReturnTheMoneyTo() {
    var payment = returnablePayment().remitterName(null).build();

    assertThat(validator.findBlockingReason(payment))
        .contains("Payment has no remitter name to return the money to");
  }

  @Test
  void blocksAReturnOfANonPositiveAmount() {
    var payment = returnablePayment().amount(BigDecimal.ZERO).build();

    assertThat(validator.findBlockingReason(payment))
        .contains("Return amount is not positive: amount=0");
  }

  @ParameterizedTest
  @EnumSource(
      value = OutgoingPaymentStatus.class,
      names = {"FAILED"},
      mode = EnumSource.Mode.EXCLUDE)
  void blocksAReturnWhoseOrderIsAlreadyAtTheBank(OutgoingPaymentStatus orderStatus) {
    var payment = returnablePayment().build();
    given(outgoingPaymentLookup.findStatusForSource(payment.getId()))
        .willReturn(Optional.of(orderStatus));

    assertThat(validator.findBlockingReason(payment))
        .contains("A return order already exists at the bank: returnOrderStatus=" + orderStatus);
  }

  @Test
  void allowsARetryOfAFailedReturnOrder() {
    var payment = returnablePayment().build();
    given(outgoingPaymentLookup.findStatusForSource(payment.getId()))
        .willReturn(Optional.of(OutgoingPaymentStatus.FAILED));

    assertThat(validator.findBlockingReason(payment)).isEmpty();
  }

  private SavingFundPayment.SavingFundPaymentBuilder returnablePayment() {
    return aPayment()
        .status(TO_BE_RETURNED)
        .amount(new BigDecimal("100.00"))
        .remitterIban(REMITTER_IBAN)
        .remitterName("Test Remitter")
        .returnReason("Kasutaja soovil");
  }
}
