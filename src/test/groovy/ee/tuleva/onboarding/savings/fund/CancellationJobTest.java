package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.VERIFIED;
import static java.time.ZoneOffset.UTC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CancellationJobTest {

  static final Instant NOW = Instant.parse("2026-10-12T14:00:00Z");

  SavingFundPaymentRepository paymentRepository = mock(SavingFundPaymentRepository.class);
  SavingFundDeadlinesService deadlinesService = mock(SavingFundDeadlinesService.class);

  CancellationJob job =
      new CancellationJob(
          paymentRepository,
          deadlinesService,
          new TransactionTemplate(mock(PlatformTransactionManager.class)),
          Clock.fixed(NOW, UTC));

  @Test
  void runJob_cancelledPaymentWithoutReturnReason_isReturnedAtCustomersRequest() {
    var payment = cancelledPayment(null);
    given(paymentRepository.findPaymentsWithStatus(VERIFIED)).willReturn(List.of(payment));
    given(deadlinesService.getCancellationDeadline(payment)).willReturn(NOW.minusSeconds(1));

    job.runJob();

    verify(paymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(paymentRepository).addReturnReason(payment.getId(), "Kasutaja soovil");
  }

  @Test
  void runJob_cancelledPaymentWithOpsReturnReason_keepsOpsReason() {
    var payment = cancelledPayment("Unsupported payment provider");
    given(paymentRepository.findPaymentsWithStatus(VERIFIED)).willReturn(List.of(payment));
    given(deadlinesService.getCancellationDeadline(payment)).willReturn(NOW.minusSeconds(1));

    job.runJob();

    verify(paymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(paymentRepository, never()).addReturnReason(any(), any());
  }

  @Test
  void runJob_cancelledPaymentBeforeDeadline_isLeftAlone() {
    var payment = cancelledPayment(null);
    given(paymentRepository.findPaymentsWithStatus(VERIFIED)).willReturn(List.of(payment));
    given(deadlinesService.getCancellationDeadline(payment)).willReturn(NOW.plusSeconds(1));

    job.runJob();

    verify(paymentRepository, never()).changeStatus(any(), any());
    verify(paymentRepository, never()).addReturnReason(any(), any());
  }

  @Test
  void runJob_paymentNotCancelled_isLeftAlone() {
    var payment =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(BigDecimal.TEN)
            .status(VERIFIED)
            .build();
    given(paymentRepository.findPaymentsWithStatus(VERIFIED)).willReturn(List.of(payment));

    job.runJob();

    verify(paymentRepository, never()).changeStatus(any(), any());
    verify(paymentRepository, never()).addReturnReason(any(), any());
  }

  private SavingFundPayment cancelledPayment(String returnReason) {
    return SavingFundPayment.builder()
        .id(UUID.randomUUID())
        .amount(BigDecimal.TEN)
        .status(VERIFIED)
        .cancelledAt(NOW.minusSeconds(3600))
        .returnReason(returnReason)
        .build();
  }
}
