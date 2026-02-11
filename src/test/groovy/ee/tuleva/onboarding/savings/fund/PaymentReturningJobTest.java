package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPaymentFixture.aPayment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.savings.fund.notification.PaymentsReturnedEvent;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentReturningJobTest {

  @Mock private SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock private PaymentReturningService paymentReturningService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private PaymentReturningJob job;

  @Test
  void runJob_publishesPaymentsReturnedEvent() {
    var payment1 = aPayment().status(TO_BE_RETURNED).amount(new BigDecimal("100.00")).build();
    var payment2 = aPayment().status(TO_BE_RETURNED).amount(new BigDecimal("50.00")).build();
    var payments = List.of(payment1, payment2);

    when(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED)).thenReturn(payments);

    job.runJob();

    verify(eventPublisher).publishEvent(new PaymentsReturnedEvent(2, new BigDecimal("150.00")));
  }

  @Test
  void runJob_reportsOnlySuccessfulReturnsInEvent() {
    var payment1 = aPayment().status(TO_BE_RETURNED).amount(new BigDecimal("100.00")).build();
    var payment2 = aPayment().status(TO_BE_RETURNED).amount(new BigDecimal("50.00")).build();
    var payments = List.of(payment1, payment2);

    when(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED)).thenReturn(payments);
    doNothing().when(paymentReturningService).createReturn(payment1);
    doThrow(new RuntimeException("return failed"))
        .when(paymentReturningService)
        .createReturn(payment2);

    job.runJob();

    verify(eventPublisher).publishEvent(new PaymentsReturnedEvent(1, new BigDecimal("100.00")));
  }

  @Test
  void runJob_doesNotPublishEventWhenNoPayments() {
    when(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED)).thenReturn(List.of());

    job.runJob();

    verify(eventPublisher, never()).publishEvent(any(PaymentsReturnedEvent.class));
  }
}
