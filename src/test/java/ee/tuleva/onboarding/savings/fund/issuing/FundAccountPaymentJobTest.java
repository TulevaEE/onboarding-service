package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.ISSUED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.PROCESSED;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.FUND_INVESTMENT_EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.payment.PaymentRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundAccountPaymentJobTest {

  @Mock SwedbankGatewayClient swedbankGatewayClient;
  @Mock SwedbankAccountConfiguration swedbankAccountConfiguration;
  @Mock SavingFundPaymentRepository savingFundPaymentRepository;
  @InjectMocks FundAccountPaymentJob job;

  @Test
  void createPayment() {
    var payments =
        List.of(
            SavingFundPayment.builder().id(UUID.randomUUID()).amount(new BigDecimal("40")).build(),
            SavingFundPayment.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("50.40"))
                .build());
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
  }
}
