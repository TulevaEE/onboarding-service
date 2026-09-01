package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNATTRIBUTED_PAYMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class PaymentVerificationNotificationIntegrationTest {

  private static final String REMITTER_CODE = "48808080880";
  private static final String OTHER_CODE = "39909090994";
  private static final BigDecimal AMOUNT = new BigDecimal("125.00");
  private static final String CODE_MISMATCH =
      "selgituses olev isikukood ei klapi maksja isikukoodiga";
  private static final String MANDRILL_MESSAGE_ID = "mandrill-savings-payment-failed";

  @Autowired private PaymentVerificationService paymentVerificationService;
  @Autowired private SavingFundPaymentRepository paymentRepository;
  @Autowired private SavingsFundLedger savingsFundLedger;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private EmailService emailService;
  @MockitoBean private OperationsNotificationService notificationService;

  private final List<UUID> createdPaymentIds = new ArrayList<>();

  @BeforeEach
  @AfterEach
  void cleanUp() {
    createdPaymentIds.forEach(this::deletePayment);
    createdPaymentIds.clear();
    deleteFailureEmails();
    userRepository.findByPersonalCode(REMITTER_CODE).ifPresent(userRepository::delete);
  }

  @Test
  void emailFailureDoesNotRollBackTheVerificationDecision() {
    savePayer();
    var payment = receivedPaymentWithMismatchedCode();
    given(emailService.send(any(), any(), any())).willThrow(new RuntimeException("Mandrill down"));

    paymentVerificationService.process(payment);

    var persisted = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(TO_BE_RETURNED);
    assertThat(persisted.getReturnReason()).isEqualTo(CODE_MISMATCH);
    assertThat(savingsFundLedger.hasLedgerEntry(payment.getId(), UNATTRIBUTED_PAYMENT)).isTrue();
    verify(notificationService).sendMessage(anyString(), eq(SAVINGS));
  }

  @Test
  void bothNotificationsFireOnTheFailurePath() {
    var payer = savePayer();
    var payment = receivedPaymentWithMismatchedCode();

    paymentVerificationService.process(payment);

    verify(notificationService)
        .sendMessage(
            "Savings fund unattributed payment: amount=%s EUR, reason=%s, paymentId=%s"
                .formatted(AMOUNT, CODE_MISMATCH, payment.getId()),
            SAVINGS);
    verify(emailService)
        .send(
            argThat(user -> payer.getPersonalCode().equals(user.getPersonalCode())),
            any(),
            eq("savings_fund_payment_failed_et"));
  }

  @Test
  void theFailureEmailIsRecordedInTheDatabase() {
    savePayer();
    var payment = receivedPaymentWithMismatchedCode();
    var mandrillResponse = mock(MandrillMessageStatus.class);
    given(mandrillResponse.getId()).willReturn(MANDRILL_MESSAGE_ID);
    given(mandrillResponse.getStatus()).willReturn("sent");
    given(emailService.send(any(), any(), eq("savings_fund_payment_failed_et")))
        .willReturn(Optional.of(mandrillResponse));

    paymentVerificationService.process(payment);

    verify(emailService).send(any(), any(), eq("savings_fund_payment_failed_et"));
    assertThat(persistedFailureEmailTypes()).containsExactly("SAVINGS_FUND_PAYMENT_FAIL");
  }

  @Test
  void noNotificationFiresWhenTheTransactionRollsBack() {
    savePayer();
    var payment = receivedPaymentWithMismatchedCode();

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      paymentVerificationService.process(payment);
                      throw new DeliberateRollback();
                    }))
        .isInstanceOf(DeliberateRollback.class);

    verifyNoInteractions(notificationService);
    verify(emailService, never()).send(any(), any(), any());
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(RECEIVED);
    assertThat(savingsFundLedger.hasLedgerEntry(payment.getId(), UNATTRIBUTED_PAYMENT)).isFalse();
  }

  private User savePayer() {
    return userRepository.save(
        User.builder()
            .firstName("Mari")
            .lastName("Maksja")
            .personalCode(REMITTER_CODE)
            .email("mari.maksja@example.com")
            .active(true)
            .build());
  }

  private SavingFundPayment receivedPaymentWithMismatchedCode() {
    var paymentId =
        paymentRepository.savePaymentData(
            SavingFundPayment.builder()
                .amount(AMOUNT)
                .description("Makse " + OTHER_CODE)
                .remitterName("Mari Maksja")
                .remitterIdCode(REMITTER_CODE)
                .remitterIban("EE982200221234567890")
                .beneficiaryName("TULEVA TÄIENDAV KOGUMISFOND")
                .beneficiaryIdCode("14118923")
                .beneficiaryIban("EE442200221092874625")
                .externalId("notification-tx-" + UUID.randomUUID())
                .receivedBefore(Instant.parse("2026-06-12T13:35:00Z"))
                .build());
    createdPaymentIds.add(paymentId);
    paymentRepository.changeStatus(paymentId, RECEIVED);
    return paymentRepository.findById(paymentId).orElseThrow();
  }

  private List<String> persistedFailureEmailTypes() {
    return jdbcClient
        .sql("select type from email where mandrill_message_id = :messageId")
        .param("messageId", MANDRILL_MESSAGE_ID)
        .query(String.class)
        .list();
  }

  private void deleteFailureEmails() {
    jdbcClient
        .sql("delete from email where mandrill_message_id = :messageId")
        .param("messageId", MANDRILL_MESSAGE_ID)
        .update();
  }

  private void deletePayment(UUID paymentId) {
    jdbcClient
        .sql(
            """
            delete from ledger.entry
            where transaction_id in (select id from ledger.transaction where external_reference = :ref)
            """)
        .param("ref", paymentId)
        .update();
    jdbcClient
        .sql("delete from ledger.transaction where external_reference = :ref")
        .param("ref", paymentId)
        .update();
    jdbcClient
        .sql("delete from saving_fund_payment where id = :id")
        .param("id", paymentId)
        .update();
  }

  private static class DeliberateRollback extends RuntimeException {}
}
