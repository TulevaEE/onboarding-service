package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNATTRIBUTED_PAYMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
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
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkRepository;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private static final String CHILD_CODE = "61506150006";
  private static final String REGISTRY_CODE = "77700123";
  private static final UUID COMPANY_ID = UUID.fromString("77700123-0000-4000-8000-000000000001");
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
  @Autowired private ParentChildLinkRepository parentChildLinkRepository;
  @Autowired private ParentChildLinkService parentChildLinkService;

  @MockitoBean private EmailService emailService;
  @MockitoBean private OperationsNotificationService notificationService;

  private final List<UUID> createdPaymentIds = new ArrayList<>();

  @BeforeEach
  @AfterEach
  void cleanUp() {
    createdPaymentIds.forEach(this::deletePayment);
    createdPaymentIds.clear();
    deleteFailureEmails();
    deleteCompany();
    parentChildLinkRepository.findAll().stream()
        .filter(link -> REMITTER_CODE.equals(link.getParentPersonalCode()))
        .forEach(parentChildLinkRepository::delete);
    userRepository.findByPersonalCode(REMITTER_CODE).ifPresent(userRepository::delete);
    userRepository.findByPersonalCode(CHILD_CODE).ifPresent(userRepository::delete);
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

  @Test
  void aFailedPaymentIntoAChildsAccountOpensTheRetryInTheChildAccountTheRolesEndpointLists() {
    savePayer();
    saveChild();
    parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(REMITTER_CODE)
            .childPersonalCode(CHILD_CODE)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .status(ACTIVE)
            .validUntil(LocalDate.of(2099, 1, 1))
            .build());
    var payment = receivedPayment("Makse " + CHILD_CODE);

    paymentVerificationService.process(payment);

    var accountIdInRoles =
        parentChildLinkService.findActivelyRepresentedChildren(REMITTER_CODE).get(CHILD_CODE);
    verify(emailService)
        .newMandrillMessage(
            "mari.maksja@example.com",
            "savings_fund_payment_failed_et",
            Map.of(
                "fname",
                "Mari",
                "lname",
                "Maksja",
                "recipientIsChild",
                true,
                "recipientAccountId",
                accountIdInRoles.toString(),
                "recipientName",
                "Kadri Maksja"),
            List.of("savings_fund"));
  }

  @Test
  void aBoardMembersFailedPaymentIntoTheCompanyOpensTheRetryInTheCompanyAccount() {
    savePayer();
    saveCompany();
    saveBoardMembership();
    var payment = receivedPayment("Makse " + REGISTRY_CODE);

    paymentVerificationService.process(payment);

    verify(emailService)
        .newMandrillMessage(
            "mari.maksja@example.com",
            "savings_fund_payment_failed_et",
            Map.of(
                "fname",
                "Mari",
                "lname",
                "Maksja",
                "recipientIsCompany",
                true,
                "recipientAccountId",
                COMPANY_ID.toString(),
                "recipientName",
                "Mesila"),
            List.of("savings_fund"));
  }

  @Test
  void aPersonsFailedPaymentNamingACompanyTheyDoNotRepresentKeepsTheirOwnRetryLink() {
    savePayer();
    saveCompany();
    var payment = receivedPayment("Makse " + REGISTRY_CODE);

    paymentVerificationService.process(payment);

    verify(emailService)
        .newMandrillMessage(
            "mari.maksja@example.com",
            "savings_fund_payment_failed_et",
            Map.of("fname", "Mari", "lname", "Maksja"),
            List.of("savings_fund"));
  }

  @Test
  void aPersonsFailedPaymentNamingSomeoneTheyDoNotRepresentKeepsTheirOwnRetryLink() {
    savePayer();
    var payment = receivedPaymentWithMismatchedCode();

    paymentVerificationService.process(payment);

    verify(emailService)
        .newMandrillMessage(
            "mari.maksja@example.com",
            "savings_fund_payment_failed_et",
            Map.of("fname", "Mari", "lname", "Maksja"),
            List.of("savings_fund"));
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

  private void saveChild() {
    userRepository.save(
        User.builder()
            .firstName("Kadri")
            .lastName("Maksja")
            .personalCode(CHILD_CODE)
            .active(true)
            .build());
  }

  private void saveCompany() {
    jdbcClient
        .sql("insert into company (id, registry_code, name) values (:id, :registryCode, 'Mesila')")
        .param("id", COMPANY_ID)
        .param("registryCode", REGISTRY_CODE)
        .update();
  }

  private void saveBoardMembership() {
    jdbcClient
        .sql(
            """
            insert into company_party (party_code, party_type, company_id, relationship_type)
            values (:partyCode, 'PERSON', :companyId, 'BOARD_MEMBER')
            """)
        .param("partyCode", REMITTER_CODE)
        .param("companyId", COMPANY_ID)
        .update();
  }

  private void deleteCompany() {
    jdbcClient
        .sql("delete from company_party where company_id = :companyId")
        .param("companyId", COMPANY_ID)
        .update();
    jdbcClient.sql("delete from company where id = :id").param("id", COMPANY_ID).update();
  }

  private SavingFundPayment receivedPaymentWithMismatchedCode() {
    return receivedPayment("Makse " + OTHER_CODE);
  }

  private SavingFundPayment receivedPayment(String description) {
    var paymentId =
        paymentRepository.savePaymentData(
            SavingFundPayment.builder()
                .amount(AMOUNT)
                .description(description)
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
