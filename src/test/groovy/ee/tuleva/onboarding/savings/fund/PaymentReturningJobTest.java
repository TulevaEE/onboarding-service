package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.RETURN_BLOCKED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.FROZEN;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPaymentFixture.aPayment;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening.NOT_CHECKED;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening.SANCTION_HIT;
import static ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening.UNAVAILABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening;
import ee.tuleva.onboarding.savings.fund.notification.SanctionedPaymentFrozenEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentReturningJobTest {

  @Mock private SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock private PaymentReturningService paymentReturningService;
  @Mock private PaymentReturnValidator paymentReturnValidator;
  @Mock private PaymentCheckService paymentCheckService;
  @Mock private PayoutAccountPayerScreening payoutAccountPayerScreening;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private TransactionTemplate transactionTemplate;

  @InjectMocks private PaymentReturningJob job;

  @BeforeEach
  void setUp() {
    lenient().when(paymentReturnValidator.findBlockingReason(any())).thenReturn(Optional.empty());
    lenient().when(payoutAccountPayerScreening.screen(any())).thenReturn(NOT_CHECKED);
    lenient()
        .doAnswer(
            invocation -> {
              Consumer<TransactionStatus> callback = invocation.getArgument(0);
              callback.accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
  }

  @Test
  void runJob_freezesAPayoutAccountPaymentWhosePayerHasASanctionsHit() {
    var payment = aPayment().status(TO_BE_RETURNED).amount(new BigDecimal("1006.87")).build();
    given(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED))
        .willReturn(List.of(payment));
    given(payoutAccountPayerScreening.screen(payment)).willReturn(SANCTION_HIT);

    job.runJob();

    verify(paymentReturningService, never()).createReturn(any());
    verify(transactionTemplate).executeWithoutResult(any());
    verify(savingFundPaymentRepository).recordPayerNameScreening(payment.getId(), SANCTION_HIT);
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), FROZEN);
    verify(eventPublisher)
        .publishEvent(new SanctionedPaymentFrozenEvent(payment.getId(), new BigDecimal("1006.87")));
  }

  @Test
  void runJob_holdsAPayoutAccountPaymentWhileScreeningIsUnavailable() {
    var payment = aPayment().status(TO_BE_RETURNED).build();
    given(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED))
        .willReturn(List.of(payment));
    given(payoutAccountPayerScreening.screen(payment)).willReturn(UNAVAILABLE);

    job.runJob();

    verify(paymentReturningService, never()).createReturn(any());
    verify(savingFundPaymentRepository, never()).changeStatus(any(), any());
    verify(savingFundPaymentRepository, never()).recordPayerNameScreening(any(), any());
    verify(paymentCheckService)
        .recordStoppedPayment(
            RETURN_BLOCKED,
            payment.getId().toString(),
            "Payer sanctions screening is unavailable, try again later");
  }

  @ParameterizedTest
  @EnumSource(
      value = Screening.class,
      names = {"CLEAR", "PEP_HIT"})
  void runJob_returnsAPayoutAccountPaymentWhosePayerHasNoSanctionsHit(Screening screening) {
    var payment = aPayment().status(TO_BE_RETURNED).build();
    given(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED))
        .willReturn(List.of(payment));
    given(payoutAccountPayerScreening.screen(payment)).willReturn(screening);

    job.runJob();

    verify(savingFundPaymentRepository).recordPayerNameScreening(payment.getId(), screening);
    verify(paymentReturningService).createReturn(payment);
  }

  @Test
  void runJob_skipsABlockedReturnAndRecordsTheReason() {
    var payment = aPayment().status(TO_BE_RETURNED).build();
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
    var payment1 = aPayment().status(TO_BE_RETURNED).build();
    var payment2 = aPayment().status(TO_BE_RETURNED).build();

    given(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED))
        .willReturn(List.of(payment1, payment2));

    job.runJob();

    verify(paymentReturningService).createReturn(payment1);
    verify(paymentReturningService).createReturn(payment2);
  }

  @Test
  void runJob_carriesOnAfterAFailedReturn() {
    var payment1 = aPayment().status(TO_BE_RETURNED).build();
    var payment2 = aPayment().status(TO_BE_RETURNED).build();

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

  @Test
  void runJob_carriesOnWhenScreeningOnePayerFails() {
    var payment1 = aPayment().status(TO_BE_RETURNED).build();
    var payment2 = aPayment().status(TO_BE_RETURNED).build();
    given(savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED))
        .willReturn(List.of(payment1, payment2));
    willThrow(new IllegalStateException("screening answer unreadable"))
        .given(payoutAccountPayerScreening)
        .screen(payment1);

    job.runJob();

    verify(paymentReturningService, never()).createReturn(payment1);
    verify(paymentReturningService).createReturn(payment2);
  }
}
