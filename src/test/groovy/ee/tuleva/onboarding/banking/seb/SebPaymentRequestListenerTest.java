package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.payment.PaymentIntegrityCheck.FIELD_MISMATCH;
import static ee.tuleva.onboarding.banking.seb.Seb.BIC;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static java.math.BigDecimal.TEN;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.payment.PaymentBlockedEvent;
import ee.tuleva.onboarding.banking.payment.PaymentFileIntegrityValidator;
import ee.tuleva.onboarding.banking.payment.PaymentIntegrityException;
import ee.tuleva.onboarding.banking.payment.PaymentIntegrityViolation;
import ee.tuleva.onboarding.banking.payment.PaymentMessageGenerator;
import ee.tuleva.onboarding.banking.payment.PaymentMisroutedEvent;
import ee.tuleva.onboarding.banking.payment.PaymentRequest;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SebPaymentRequestListenerTest {

  private static final String SEB_IBAN = "EE111111111111111111";

  @Mock private SebGatewayClient sebGatewayClient;
  @Mock private BankAccounts bankAccounts;
  @Mock private PaymentMessageGenerator paymentMessageGenerator;
  @Mock private PaymentFileIntegrityValidator paymentFileIntegrityValidator;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private SebPaymentRequestListener listener;

  @Test
  void onRequestPayment_sendsPaymentToSebGateway() {
    var paymentRequest = paymentRequest(SEB_IBAN);
    var event = new RequestPaymentEvent(paymentRequest, UUID.randomUUID());

    when(bankAccounts.find(SEB_IBAN))
        .thenReturn(Optional.of(new BankAccount(SEB_IBAN, WITHDRAWAL_EUR, TKF100, "1162")));
    when(paymentMessageGenerator.generatePaymentMessage(paymentRequest, BIC))
        .thenReturn("<xml>payment</xml>");
    when(paymentFileIntegrityValidator.validate("<xml>payment</xml>", paymentRequest))
        .thenReturn(List.of());

    listener.onRequestPayment(event);

    verify(sebGatewayClient).submitPaymentFile("<xml>payment</xml>", "end-to-end-123", "1162");
  }

  @Test
  void onRequestPayment_doesNotSubmitAndAlertsWhenFileDoesNotMatchTheRequest() {
    var paymentRequest = paymentRequest(SEB_IBAN);
    var event = new RequestPaymentEvent(paymentRequest, UUID.randomUUID());
    var violations = List.of(new PaymentIntegrityViolation(FIELD_MISMATCH, "beneficiaryIban"));

    when(bankAccounts.find(SEB_IBAN))
        .thenReturn(Optional.of(new BankAccount(SEB_IBAN, WITHDRAWAL_EUR, TKF100, "1162")));
    when(paymentMessageGenerator.generatePaymentMessage(paymentRequest, BIC))
        .thenReturn("<xml>tampered</xml>");
    when(paymentFileIntegrityValidator.validate("<xml>tampered</xml>", paymentRequest))
        .thenReturn(violations);

    assertThatThrownBy(() -> listener.onRequestPayment(event))
        .isInstanceOf(PaymentIntegrityException.class);

    verify(sebGatewayClient, never()).submitPaymentFile(anyString(), anyString(), anyString());
    verify(eventPublisher).publishEvent(new PaymentBlockedEvent(paymentRequest, violations));
  }

  @Test
  void onRequestPayment_alertsWhenRemitterIsNotASebAccount() {
    var nonSebIban = "EE333333333333333333";
    var paymentRequest = paymentRequest(nonSebIban);
    var event = new RequestPaymentEvent(paymentRequest, UUID.randomUUID());

    when(bankAccounts.find(nonSebIban)).thenReturn(Optional.empty());

    listener.onRequestPayment(event);

    verify(sebGatewayClient, never()).submitPaymentFile(anyString(), anyString(), anyString());
    verify(paymentMessageGenerator, never()).generatePaymentMessage(any(), anyString());
    verify(eventPublisher).publishEvent(new PaymentMisroutedEvent(paymentRequest));
  }

  private PaymentRequest paymentRequest(String remitterIban) {
    return PaymentRequest.builder()
        .remitterName("Tuleva Täiendav Kogumisfond")
        .remitterId("1162")
        .remitterIban(remitterIban)
        .beneficiaryName("John Doe")
        .beneficiaryIban("EE222222222222222222")
        .amount(TEN)
        .description("test payment")
        .ourId("123")
        .endToEndId("end-to-end-123")
        .build();
  }
}
