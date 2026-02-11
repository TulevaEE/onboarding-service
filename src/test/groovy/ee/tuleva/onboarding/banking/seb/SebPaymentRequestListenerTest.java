package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.seb.Seb.BIC;
import static java.math.BigDecimal.TEN;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.payment.PaymentMessageGenerator;
import ee.tuleva.onboarding.banking.payment.PaymentRequest;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebPaymentRequestListenerTest {

  private static final String SEB_IBAN = "EE111111111111111111";

  @Mock private SebGatewayClient sebGatewayClient;
  @Mock private SebAccountConfiguration sebAccountConfiguration;
  @Mock private PaymentMessageGenerator paymentMessageGenerator;

  @InjectMocks private SebPaymentRequestListener listener;

  @Test
  void onRequestPayment_sendsPaymentToSebGateway() {
    var paymentRequest =
        PaymentRequest.builder()
            .remitterName("Tuleva Täiendav Kogumisfond")
            .remitterId("1162")
            .remitterIban(SEB_IBAN)
            .beneficiaryName("John Doe")
            .beneficiaryIban("EE461277288334943840")
            .amount(TEN)
            .description("test payment")
            .ourId("123")
            .endToEndId("end-to-end-123")
            .build();

    var event = new RequestPaymentEvent(paymentRequest, UUID.randomUUID());

    when(sebAccountConfiguration.getAccountType(SEB_IBAN)).thenReturn(WITHDRAWAL_EUR);
    when(paymentMessageGenerator.generatePaymentMessage(paymentRequest, BIC))
        .thenReturn("<xml>payment</xml>");

    listener.onRequestPayment(event);

    verify(sebGatewayClient).submitPaymentFile("<xml>payment</xml>", "end-to-end-123");
  }

  @Test
  void onRequestPayment_ignoresNonSebAccounts() {
    var nonSebIban = "EE999999999999999999";
    var paymentRequest =
        PaymentRequest.builder()
            .remitterName("Tuleva Täiendav Kogumisfond")
            .remitterId("1162")
            .remitterIban(nonSebIban)
            .beneficiaryName("John Doe")
            .beneficiaryIban("EE461277288334943840")
            .amount(TEN)
            .description("test payment")
            .ourId("123")
            .endToEndId("end-to-end-123")
            .build();

    var event = new RequestPaymentEvent(paymentRequest, UUID.randomUUID());

    when(sebAccountConfiguration.getAccountType(nonSebIban)).thenReturn(null);

    listener.onRequestPayment(event);

    verify(sebGatewayClient, never()).submitPaymentFile(anyString(), anyString());
    verify(paymentMessageGenerator, never()).generatePaymentMessage(any(), anyString());
  }
}
