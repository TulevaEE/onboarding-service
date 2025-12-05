package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.event.TrackableEventType.SUBSCRIPTION_BATCH_CREATED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.ISSUED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.PROCESSED;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.FUND_INVESTMENT_EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.swedbank.payment.PaymentRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

class FundAccountPaymentJobTest {

  SwedbankGatewayClient swedbankGatewayClient = mock();
  SwedbankAccountConfiguration swedbankAccountConfiguration = mock();
  SavingFundPaymentRepository savingFundPaymentRepository = mock();
  TransactionTemplate transactionTemplate = mock();
  ApplicationEventPublisher eventPublisher = mock();

  FundAccountPaymentJob job =
      new FundAccountPaymentJob(
          swedbankGatewayClient,
          swedbankAccountConfiguration,
          savingFundPaymentRepository,
          transactionTemplate,
          eventPublisher,
          new EndToEndIdConverter());

  @Test
  @SuppressWarnings("unchecked")
  void createPayment() {
    var paymentId1 = UUID.randomUUID();
    var paymentId2 = UUID.randomUUID();
    var payments =
        List.of(
            SavingFundPayment.builder().id(paymentId1).amount(new BigDecimal("40")).build(),
            SavingFundPayment.builder().id(paymentId2).amount(new BigDecimal("50.40")).build());
    when(savingFundPaymentRepository.findPaymentsWithStatus(ISSUED)).thenReturn(payments);
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("investment-IBAN");
    when(swedbankAccountConfiguration.getAccountIban(DEPOSIT_EUR)).thenReturn("deposit-IBAN");

    job.createPaymentRequest();

    verify(savingFundPaymentRepository).changeStatus(payments.get(0).getId(), PROCESSED);
    verify(savingFundPaymentRepository).changeStatus(payments.get(1).getId(), PROCESSED);

    var paymentRequestCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
    verify(swedbankGatewayClient).sendPaymentRequest(paymentRequestCaptor.capture(), any());

    var paymentRequest = paymentRequestCaptor.getValue();
    assertThat(paymentRequest.amount()).isEqualTo(new BigDecimal("90.40"));
    assertThat(paymentRequest.remitterName()).isEqualTo("Tuleva Fondid AS");
    assertThat(paymentRequest.remitterIban()).isEqualTo("deposit-IBAN");
    assertThat(paymentRequest.beneficiaryName()).isEqualTo("Tuleva Fondid AS");
    assertThat(paymentRequest.beneficiaryIban()).isEqualTo("investment-IBAN");
    assertThat(paymentRequest.description()).isEqualTo("Subscriptions");

    var eventCaptor = ArgumentCaptor.forClass(TrackableSystemEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    var event = eventCaptor.getValue();
    assertThat(event.getType()).isEqualTo(SUBSCRIPTION_BATCH_CREATED);
    assertThat(event.getData().get("batchId")).isNotNull();
    assertThat((List<UUID>) event.getData().get("paymentIds"))
        .containsExactlyInAnyOrder(paymentId1, paymentId2);
    assertThat(event.getData().get("paymentCount")).isEqualTo(2);
    assertThat(event.getData().get("totalAmount")).isEqualTo(new BigDecimal("90.40"));
  }

  @Test
  void createPaymentRequest_doesNotSendPaymentWhenNoIssuedPayments() {
    when(savingFundPaymentRepository.findPaymentsWithStatus(ISSUED)).thenReturn(List.of());

    job.createPaymentRequest();

    verify(swedbankGatewayClient, never()).sendPaymentRequest(any(), any());
  }
}
