package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.payment.OutgoingPaymentLookup;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingFundPayment.Status;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboundReturnVerifierTest {

  private static final UUID PAYMENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

  @Mock OutgoingPaymentLookup outgoingPaymentLookup;
  @InjectMocks OutboundReturnVerifier verifier;

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"TO_BE_RETURNED", "RETURNED"})
  void executedReturnIsRefusedEvenWhenOperatorClaimsTheReturnWasCancelled(Status paymentStatus) {
    givenReturnOrder(EXECUTED);

    assertThatThrownBy(() -> verifier.verifyAttributable(payment(paymentStatus), true))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"TO_BE_RETURNED", "RETURNED"})
  void executedReturnIsRefusedWithoutAttestation(Status paymentStatus) {
    givenReturnOrder(EXECUTED);

    assertThatThrownBy(() -> verifier.verifyAttributable(payment(paymentStatus), false))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = OutgoingPaymentStatus.class,
      names = {"ATTEMPTED", "SUBMITTED"})
  void returnOrderStillLiveAtTheBankNeedsAttestation(OutgoingPaymentStatus orderStatus) {
    givenReturnOrder(orderStatus);

    assertThatThrownBy(() -> verifier.verifyAttributable(payment(TO_BE_RETURNED), false))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = OutgoingPaymentStatus.class,
      names = {"ATTEMPTED", "SUBMITTED"})
  void returnOrderStillLiveAtTheBankIsAllowedWithAttestation(OutgoingPaymentStatus orderStatus) {
    givenReturnOrder(orderStatus);

    assertThatCode(() -> verifier.verifyAttributable(payment(RETURNED), true))
        .doesNotThrowAnyException();
  }

  @Test
  void failedReturnOrderNeedsNoAttestation() {
    givenReturnOrder(FAILED);

    assertThatCode(() -> verifier.verifyAttributable(payment(RETURNED), false))
        .doesNotThrowAnyException();
  }

  @Test
  void paymentAwaitingReturnWithNoOrderRecordedNeedsNoAttestation() {
    givenNoReturnOrder();

    assertThatCode(() -> verifier.verifyAttributable(payment(TO_BE_RETURNED), false))
        .doesNotThrowAnyException();
  }

  @Test
  void returnedPaymentWithNoOrderRecordedNeedsAttestation() {
    givenNoReturnOrder();

    assertThatThrownBy(() -> verifier.verifyAttributable(payment(RETURNED), false))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void returnedPaymentWithNoOrderRecordedIsAllowedWithAttestation() {
    givenNoReturnOrder();

    assertThatCode(() -> verifier.verifyAttributable(payment(RETURNED), true))
        .doesNotThrowAnyException();
  }

  private void givenReturnOrder(OutgoingPaymentStatus status) {
    given(outgoingPaymentLookup.findStatusForSource(PAYMENT_ID)).willReturn(Optional.of(status));
  }

  private void givenNoReturnOrder() {
    given(outgoingPaymentLookup.findStatusForSource(PAYMENT_ID)).willReturn(Optional.empty());
  }

  private SavingFundPayment payment(Status status) {
    return SavingFundPayment.builder()
        .id(PAYMENT_ID)
        .amount(new BigDecimal("1000.00"))
        .description("Sissemakse")
        .status(status)
        .build();
  }
}
