package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RETURNED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.payment.PaymentRequest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentReturningServiceTest {

  @Mock SwedbankGatewayClient swedbankGatewayClient;
  @Mock SavingFundPaymentRepository savingFundPaymentRepository;
  @InjectMocks PaymentReturningService service;

  @Test
  void createReturn() {
    var originalPayment =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal(60))
            .remitterName("John Doe")
            .remitterIban("IBAN-11")
            .beneficiaryName("Tuleva")
            .beneficiaryIban("IBAN-22")
            .returnReason("isikukood ei klapi")
            .build();

    service.createReturn(originalPayment);

    var expectedId = originalPayment.getId().toString().replace("-", "");
    var expectedPaymentRequest =
        PaymentRequest.builder()
            .remitterName("Tuleva Fondid AS")
            .remitterId("14118923")
            .remitterIban("IBAN-22")
            .remitterBic("HABAEE2X")
            .beneficiaryName("John Doe")
            .beneficiaryIban("IBAN-11")
            .amount(new BigDecimal(60))
            .description("Tagastus: isikukood ei klapi")
            .ourId(expectedId)
            .endToEndId(expectedId)
            .build();
    verify(swedbankGatewayClient).sendPaymentRequest(eq(expectedPaymentRequest), any());
    verify(savingFundPaymentRepository).changeStatus(originalPayment.getId(), RETURNED);
  }
}
