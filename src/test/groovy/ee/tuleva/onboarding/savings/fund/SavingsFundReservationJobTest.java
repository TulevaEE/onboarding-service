package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPaymentFixture.aPayment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.savings.fund.notification.ReservationCompletedEvent;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SavingsFundReservationJobTest {

  @Mock private SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock private PaymentReservationService paymentReservationService;
  @Mock private PaymentReservationFilterService paymentReservationFilterService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private SavingsFundReservationJob job;

  @Test
  void runJob_publishesReservationCompletedEvent() {
    var payment1 = aPayment().amount(new BigDecimal("100.00")).status(VERIFIED).build();
    var payment2 = aPayment().amount(new BigDecimal("200.00")).status(VERIFIED).build();
    var payments = List.of(payment1, payment2);

    when(savingFundPaymentRepository.findPaymentsWithStatus(VERIFIED)).thenReturn(payments);
    when(paymentReservationFilterService.filterPaymentsToReserve(payments)).thenReturn(payments);

    job.runJob();

    verify(eventPublisher).publishEvent(new ReservationCompletedEvent(2, new BigDecimal("300.00")));
  }

  @Test
  void runJob_reportsOnlySuccessfulReservationsInEvent() {
    var payment1 = aPayment().amount(new BigDecimal("100.00")).status(VERIFIED).build();
    var payment2 = aPayment().amount(new BigDecimal("200.00")).status(VERIFIED).build();
    var payments = List.of(payment1, payment2);

    when(savingFundPaymentRepository.findPaymentsWithStatus(VERIFIED)).thenReturn(payments);
    when(paymentReservationFilterService.filterPaymentsToReserve(payments)).thenReturn(payments);
    doNothing().when(paymentReservationService).process(payment1);
    doThrow(new RuntimeException("reservation failed"))
        .when(paymentReservationService)
        .process(payment2);

    job.runJob();

    verify(eventPublisher).publishEvent(new ReservationCompletedEvent(1, new BigDecimal("100.00")));
  }

  @Test
  void runJob_doesNotPublishEventWhenNoPayments() {
    when(savingFundPaymentRepository.findPaymentsWithStatus(VERIFIED)).thenReturn(List.of());
    when(paymentReservationFilterService.filterPaymentsToReserve(List.of())).thenReturn(List.of());

    job.runJob();

    verify(eventPublisher, never()).publishEvent(any(ReservationCompletedEvent.class));
  }
}
