package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.RETURN_BLOCKED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPaymentFixture.aPayment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentReturningJobTest {

  @Mock private SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock private PaymentReturningService paymentReturningService;
  @Mock private PaymentReturnValidator paymentReturnValidator;
  @Mock private PaymentCheckService paymentCheckService;

  @InjectMocks private PaymentReturningJob job;

  @BeforeEach
  void setUp() {
    lenient().when(paymentReturnValidator.findBlockingReason(any())).thenReturn(Optional.empty());
  }

  @Test
  void runJob_skipsABlockedReturnAndRecordsTheReason() {
    var payment = aPayment().status(TO_BE_RETURNED).amount(new BigDecimal("100.00")).build();
    var reason = "A return order already exists at the bank: returnOrderStatus=SUBMITTED";
    given(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED))
        .willReturn(List.of(payment));
    given(paymentReturnValidator.findBlockingReason(payment)).willReturn(Optional.of(reason));

    job.runJob();

    verify(paymentReturningService, never()).createReturn(any());
    verify(paymentCheckService)
        .recordStoppedPayment(RETURN_BLOCKED, payment.getId().toString(), reason);
  }

  @Test
  void runJob_returnsEveryPaymentToBeReturned() {
    var payment1 = aPayment().status(TO_BE_RETURNED).amount(new BigDecimal("100.00")).build();
    var payment2 = aPayment().status(TO_BE_RETURNED).amount(new BigDecimal("50.00")).build();

    given(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED))
        .willReturn(List.of(payment1, payment2));

    job.runJob();

    verify(paymentReturningService).createReturn(payment1);
    verify(paymentReturningService).createReturn(payment2);
  }

  @Test
  void runJob_carriesOnAfterAFailedReturn() {
    var payment1 = aPayment().status(TO_BE_RETURNED).amount(new BigDecimal("100.00")).build();
    var payment2 = aPayment().status(TO_BE_RETURNED).amount(new BigDecimal("50.00")).build();

    given(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED))
        .willReturn(List.of(payment1, payment2));
    willThrow(new RuntimeException("return failed"))
        .given(paymentReturningService)
        .createReturn(payment1);

    job.runJob();

    verify(paymentReturningService).createReturn(payment2);
  }

  @Test
  void runJob_returnsNothingWhenNoPayments() {
    given(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED)).willReturn(List.of());

    job.runJob();

    verify(paymentReturningService, never()).createReturn(any());
  }
}
