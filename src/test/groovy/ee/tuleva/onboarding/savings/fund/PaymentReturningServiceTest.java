package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_CANCEL_REQUESTED;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_RECEIVED;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RETURNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.payment.PaymentRequest;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentReturningServiceTest {

  private static final PartyId PARTY = new PartyId(PERSON, "38812121215");

  @Mock ApplicationEventPublisher eventPublisher;
  @Mock SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock SavingsFundLedger savingsFundLedger;
  @Mock TransactionTemplate transactionTemplate;
  EndToEndIdConverter endToEndIdConverter = new EndToEndIdConverter();

  PaymentReturningService service;

  @BeforeEach
  void setUp() {
    doAnswer(
            invocation -> {
              Consumer<TransactionStatus> callback = invocation.getArgument(0);
              callback.accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
    service =
        new PaymentReturningService(
            eventPublisher,
            savingFundPaymentRepository,
            savingsFundLedger,
            endToEndIdConverter,
            transactionTemplate);
  }

  @Test
  void createReturn_reservesTheBalanceBeforeSendingAndMarksItReturnedOnlyOnceTheOrderIsOut() {
    var payment = creditedPayment();
    given(savingsFundLedger.hasLedgerEntry(payment.getId(), PAYMENT_RECEIVED)).willReturn(true);

    service.createReturn(payment);

    var inOrder = inOrder(savingsFundLedger, eventPublisher, savingFundPaymentRepository);
    inOrder
        .verify(savingsFundLedger)
        .reservePaymentForCancellation(
            LedgerRefs.from(PARTY), payment.getAmount(), payment.getId());
    inOrder.verify(eventPublisher).publishEvent(any(RequestPaymentEvent.class));
    inOrder.verify(savingFundPaymentRepository).changeStatus(payment.getId(), RETURNED);
  }

  @Test
  void createReturn_whenTheLedgerReservationFails_sendsNothingToTheBankAndLeavesTheStatusAlone() {
    var payment = creditedPayment();
    given(savingsFundLedger.hasLedgerEntry(payment.getId(), PAYMENT_RECEIVED)).willReturn(true);
    willThrow(new IllegalStateException("Insufficient balance"))
        .given(savingsFundLedger)
        .reservePaymentForCancellation(any(), any(), any());

    assertThatThrownBy(() -> service.createReturn(payment))
        .isInstanceOf(IllegalStateException.class);

    verify(eventPublisher, never()).publishEvent(any(RequestPaymentEvent.class));
    verify(savingFundPaymentRepository, never()).changeStatus(any(), any());
  }

  @Test
  void createReturn_whenTheBankRejectsTheOrder_leavesThePaymentToBeReturned() {
    var payment = creditedPayment();
    given(savingsFundLedger.hasLedgerEntry(payment.getId(), PAYMENT_RECEIVED)).willReturn(true);
    willThrow(new IllegalStateException("Payment file failed integrity validation"))
        .given(eventPublisher)
        .publishEvent(any(RequestPaymentEvent.class));

    assertThatThrownBy(() -> service.createReturn(payment))
        .isInstanceOf(IllegalStateException.class);

    verify(savingFundPaymentRepository, never()).changeStatus(any(), any());
  }

  @Test
  void createReturn_whenTheBalanceIsAlreadyReserved_doesNotReserveItASecondTime() {
    var payment = creditedPayment();
    given(savingsFundLedger.hasLedgerEntry(payment.getId(), PAYMENT_RECEIVED)).willReturn(true);
    given(savingsFundLedger.hasLedgerEntry(payment.getId(), PAYMENT_CANCEL_REQUESTED))
        .willReturn(true);

    service.createReturn(payment);

    verify(savingsFundLedger, never()).reservePaymentForCancellation(any(), any(), any());
    verify(eventPublisher).publishEvent(any(RequestPaymentEvent.class));
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), RETURNED);
  }

  private SavingFundPayment creditedPayment() {
    return SavingFundPayment.builder()
        .id(UUID.randomUUID())
        .partyId(PARTY)
        .amount(new BigDecimal("100.00"))
        .remitterName("John Doe")
        .remitterIban("EE111111111111111111")
        .beneficiaryName("Tuleva")
        .beneficiaryIban("EE222222222222222222")
        .returnReason("Kasutaja soovil")
        .build();
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
  void createReturn_withParty_reservesPaymentForCancellation() {
    var party = new PartyId(PERSON, "38812121215");
    var paymentId = UUID.randomUUID();
    var amount = new BigDecimal("100.00");
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .partyId(party)
            .amount(amount)
            .remitterName("John Doe")
            .remitterIban("EE111111111111111111")
            .beneficiaryName("Tuleva")
            .beneficiaryIban("EE222222222222222222")
            .returnReason("Kasutaja soovil")
            .build();
    given(savingsFundLedger.hasLedgerEntry(paymentId, PAYMENT_RECEIVED)).willReturn(true);

    service.createReturn(payment);

    verify(savingsFundLedger)
        .reservePaymentForCancellation(LedgerRefs.from(party), amount, paymentId);
  }

  @Test
  void createReturn_withPartyButNeverCreditedToIt_doesNotReservePaymentForCancellation() {
    var party = new PartyId(PERSON, "38812121215");
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .partyId(party)
            .amount(new BigDecimal("2100.00"))
            .remitterName("John Doe")
            .remitterIban("EE111111111111111111")
            .beneficiaryName("Tuleva")
            .beneficiaryIban("EE222222222222222222")
            .returnReason("isik ei ole läbinud hoolsusmeetmeid")
            .build();
    given(savingsFundLedger.hasLedgerEntry(paymentId, PAYMENT_RECEIVED)).willReturn(false);

    service.createReturn(payment);

    verify(savingsFundLedger, never()).reservePaymentForCancellation(any(), any(), any());
  }

  @Test
  void createReturn_withoutParty_doesNotReservePaymentForCancellation() {
    var payment =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("50.00"))
            .remitterName("Unknown Person")
            .remitterIban("EE333333333333333333")
            .beneficiaryName("Tuleva")
            .beneficiaryIban("EE444444444444444444")
            .returnReason("isik ei ole Tuleva klient")
            .build();

    service.createReturn(payment);

    verify(savingsFundLedger, never())
        .reservePaymentForCancellation(any(PartyRef.class), any(), any());
  }
}
