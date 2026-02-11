package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RETURNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.payment.PaymentRequest;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentReturningServiceTest {

  @Mock ApplicationEventPublisher eventPublisher;
  @Mock SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock UserRepository userRepository;
  @Mock SavingsFundLedger savingsFundLedger;
  EndToEndIdConverter endToEndIdConverter = new EndToEndIdConverter();

  PaymentReturningService service;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    service =
        new PaymentReturningService(
            eventPublisher,
            savingFundPaymentRepository,
            userRepository,
            savingsFundLedger,
            endToEndIdConverter);
  }

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
            .remitterName("Tuleva Täiendav Kogumisfond")
            .remitterId("1162")
            .remitterIban("IBAN-22")
            .beneficiaryName("John Doe")
            .beneficiaryIban("IBAN-11")
            .amount(new BigDecimal(60))
            .description("Tagastus: isikukood ei klapi")
            .ourId(expectedId)
            .endToEndId(expectedId)
            .build();
    var eventCaptor = ArgumentCaptor.forClass(RequestPaymentEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().paymentRequest()).isEqualTo(expectedPaymentRequest);
    verify(savingFundPaymentRepository).changeStatus(originalPayment.getId(), RETURNED);
  }

  @Test
  void createReturn_withUserId_reservesPaymentForCancellation() {
    User user = sampleUser().build();
    var paymentId = UUID.randomUUID();
    var amount = new BigDecimal("100.00");
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .userId(user.getId())
            .amount(amount)
            .remitterName("John Doe")
            .remitterIban("EE111111111111111111")
            .beneficiaryName("Tuleva")
            .beneficiaryIban("EE222222222222222222")
            .returnReason("Kasutaja soovil")
            .build();

    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

    service.createReturn(payment);

    verify(savingsFundLedger).reservePaymentForCancellation(user, amount, paymentId);
  }

  @Test
  void createReturn_withoutUserId_doesNotReservePaymentForCancellation() {
    var payment =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .userId(null)
            .amount(new BigDecimal("50.00"))
            .remitterName("Unknown Person")
            .remitterIban("EE333333333333333333")
            .beneficiaryName("Tuleva")
            .beneficiaryIban("EE444444444444444444")
            .returnReason("isik ei ole Tuleva klient")
            .build();

    service.createReturn(payment);

    verify(savingsFundLedger, never()).reservePaymentForCancellation(any(User.class), any(), any());
  }
}
