package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RECEIVED;
import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentVerificationJobTest {

  @Mock SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock PaymentVerificationService paymentVerificationService;
  @Spy @InjectMocks PaymentVerificationJob job;

  @Test
  void runProcess() {
    var payment1 = createPayment("A");
    var payment2 = createPayment("B");
    when(savingFundPaymentRepository.findPaymentsWithStatus(any()))
        .thenReturn(List.of(payment1, payment2));

    job.runJob();

    verify(paymentVerificationService).process(payment1);
    verify(paymentVerificationService).process(payment2);
    verify(savingFundPaymentRepository).findPaymentsWithStatus(RECEIVED);
  }

  @Test
  void runProcess_handleExceptions() {
    var payment1 = createPayment("A");
    var payment2 = createPayment("B");
    when(savingFundPaymentRepository.findPaymentsWithStatus(any()))
        .thenReturn(List.of(payment1, payment2));
    doThrow(RuntimeException.class).when(paymentVerificationService).process(payment1);

    job.runJob();

    verify(paymentVerificationService).process(payment1);
    verify(paymentVerificationService).process(payment2);
  }

  private SavingFundPayment createPayment(String description) {
    return SavingFundPayment.builder().id(randomUUID()).description(description).build();
  }
}
