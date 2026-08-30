package ee.tuleva.onboarding.savings.fund.notification;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_BOUNCE_BACK;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_CANCELLED;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.ISSUED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.PROCESSED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static java.math.BigDecimal.ONE;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.disjoint;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankMessagesProcessingCompleted;
import ee.tuleva.onboarding.banking.seb.SebGatewayClient;
import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.FundNavProvider;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.LedgerRefs;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.savings.fund.issuing.FundAccountPaymentJob;
import ee.tuleva.onboarding.savings.fund.issuing.IssuingJob;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionService;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SavingsFundNotificationCommitIntegrationTest {

  private static final String REDEEMER_CODE = "48808080891";
  private static final String REDEMPTION_IBAN = "EE471000001020145685";
  private static final BigDecimal REDEMPTION_AMOUNT = new BigDecimal("25.00");
  private static final BigDecimal REDEMPTION_UNITS = new BigDecimal("25.00000");
  private static final BigDecimal HELD_CASH = new BigDecimal("1000.00");
  private static final BigDecimal HELD_UNITS = new BigDecimal("100.00000");

  private static final String FIXTURE_EXTERNAL_ID_PREFIX = "notification-commit-";
  private static final UUID HELD_UNITS_REFERENCE =
      UUID.fromString("0f5e0f1a-1f34-4c8e-9a2b-6d5c4b3a2f10");

  private static final Instant AFTER_THE_CUTOFF = Instant.parse("2025-09-29T15:00:00Z");
  private static final Instant BEFORE_THE_PREVIOUS_CUTOFF = Instant.parse("2025-09-24T09:00:00Z");
  private static final BigDecimal ISSUED_CASH = new BigDecimal("100.00");
  private static final BigDecimal ISSUED_UNITS = new BigDecimal("100.00000");
  private static final BigDecimal ISSUING_NAV = new BigDecimal("1.00000");

  private static final BigDecimal RETURNED_CASH = new BigDecimal("50.00");
  private static final BigDecimal BATCHED_CASH = new BigDecimal("75.00");

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private BankAccounts bankAccounts;
  @Autowired private FundAccountPaymentJob fundAccountPaymentJob;
  @Autowired private IssuingJob issuingJob;
  @Autowired private RedemptionService redemptionService;
  @Autowired private SavingFundPaymentRepository paymentRepository;
  @Autowired private SavingsFundLedger savingsFundLedger;
  @Autowired private SavingsFundOnboardingRepository onboardingRepository;
  @Autowired private EventLogRepository eventLogRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private OperationsNotificationService notificationService;
  @MockitoBean private FundNavProvider navProvider;
  @MockitoBean private SebGatewayClient sebGatewayClient;

  @BeforeEach
  @AfterEach
  void cleanUp() {
    ClockHolder.setDefaultClock();
    sweep(
        this::deleteEventLogsReferencingFixturePayments,
        this::deleteLedgerTransactionsThenTheRowsThatIdentifyThem,
        this::deleteLedgerPartyAndAccounts,
        this::deleteOnboardingStatus,
        this::deleteRedeemer);
  }

  @Test
  void redemptionNotificationFiresWhenTheRedemptionTransactionCommits() {
    var redeemer = onboardedRedeemerWithFundUnits();

    var request =
        redemptionService.createRedemptionRequest(
            authenticatedPersonFromUser(redeemer).build(), REDEMPTION_AMOUNT, EUR, REDEMPTION_IBAN);

    verify(notificationService)
        .sendMessage(
            "Savings fund redemption requested: requestedAmount=%s EUR, fundUnits=%s, redemptionRequestId=%s"
                .formatted(REDEMPTION_AMOUNT, REDEMPTION_UNITS, request.getId()),
            SAVINGS);
  }

  @Test
  void noRedemptionNotificationFiresWhenTheRedemptionTransactionRollsBack() {
    var redeemer = onboardedRedeemerWithFundUnits();
    var person = authenticatedPersonFromUser(redeemer).build();

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      redemptionService.createRedemptionRequest(
                          person, REDEMPTION_AMOUNT, EUR, REDEMPTION_IBAN);
                      throw new DeliberateRollback();
                    }))
        .isInstanceOf(DeliberateRollback.class);

    verifyNoInteractions(notificationService);
    assertThat(redemptionRequestIds()).isEmpty();
  }

  @Test
  void issuingNotificationFiresWhenTheIssuingTransactionCommits() {
    reservedPaymentReadyForIssuing();

    issuingJob.runJob();

    verify(notificationService)
        .sendMessage(
            "Savings fund issuing: payments=%d, totalAmount=%s EUR, fundUnitsIssued=%s, NAV=%s"
                .formatted(1, ISSUED_CASH, ISSUED_UNITS, ISSUING_NAV),
            SAVINGS);
  }

  @Test
  void noIssuingNotificationFiresWhenTheIssuingTransactionRollsBack() {
    var paymentId = reservedPaymentReadyForIssuing();

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      issuingJob.runJob();
                      throw new DeliberateRollback();
                    }))
        .isInstanceOf(DeliberateRollback.class);

    verifyNoInteractions(notificationService);
    assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(RESERVED);
  }

  @Test
  void subscriptionBatchNotificationFiresWhenTheBatchTransactionCommits() {
    issuedPaymentAwaitingSubscriptionBatch();

    fundAccountPaymentJob.runJob();

    verify(notificationService)
        .sendMessage(
            "Savings fund subscription batch sent to SEB: totalAmount=%s EUR"
                .formatted(BATCHED_CASH),
            SAVINGS);
  }

  @Test
  void noSubscriptionBatchNotificationFiresWhenTheBatchTransactionRollsBack() {
    var paymentId = issuedPaymentAwaitingSubscriptionBatch();

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      fundAccountPaymentJob.runJob();
                      throw new DeliberateRollback();
                    }))
        .isInstanceOf(DeliberateRollback.class);

    verifyNoInteractions(notificationService);
    assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(ISSUED);
  }

  private UUID issuedPaymentAwaitingSubscriptionBatch() {
    var party = new PartyId(PartyId.Type.PERSON, REDEEMER_CODE);
    var paymentId =
        paymentRepository.savePaymentData(
            SavingFundPayment.builder()
                .amount(BATCHED_CASH)
                .description("Subscription batch")
                .remitterName("Riina Raha")
                .remitterIdCode(REDEEMER_CODE)
                .remitterIban(REDEMPTION_IBAN)
                .beneficiaryName("TULEVA TÄIENDAV KOGUMISFOND")
                .beneficiaryIdCode("14118923")
                .beneficiaryIban("EE442200221092874625")
                .externalId(fixtureExternalId())
                .build());
    paymentRepository.attachParty(paymentId, party);
    paymentRepository.changeStatus(paymentId, RECEIVED);
    paymentRepository.changeStatus(paymentId, VERIFIED);
    paymentRepository.changeStatus(paymentId, RESERVED);
    paymentRepository.changeStatus(paymentId, ISSUED);
    assertNoForeignIssuedPayments();
    return paymentId;
  }

  @Test
  void deferredReturnNotificationFiresWhenTheMatchingTransactionCommits() {
    unmatchedOutgoingReturn();

    eventPublisher.publishEvent(new BankMessagesProcessingCompleted());

    verify(notificationService)
        .sendMessage(
            "Deferred return matching: matchedCount=%d, totalAmount=%s EUR"
                .formatted(1, RETURNED_CASH),
            SAVINGS);
  }

  @Test
  void noDeferredReturnNotificationFiresWhenTheMatchingTransactionRollsBack() {
    var originalPaymentId = unmatchedOutgoingReturn();

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      eventPublisher.publishEvent(new BankMessagesProcessingCompleted());
                      throw new DeliberateRollback();
                    }))
        .isInstanceOf(DeliberateRollback.class);

    verifyNoInteractions(notificationService);
    assertThat(savingsFundLedger.hasLedgerEntry(originalPaymentId, PAYMENT_BOUNCE_BACK)).isFalse();
  }

  private UUID unmatchedOutgoingReturn() {
    var originalPaymentId =
        paymentRepository.savePaymentData(
            SavingFundPayment.builder()
                .amount(RETURNED_CASH)
                .description("Unidentifiable payment")
                .remitterName("Riina Raha")
                .remitterIban(REDEMPTION_IBAN)
                .beneficiaryName("TULEVA TÄIENDAV KOGUMISFOND")
                .beneficiaryIdCode("14118923")
                .beneficiaryIban("EE442200221092874625")
                .externalId(fixtureExternalId())
                .build());

    var returnPaymentId =
        paymentRepository.savePaymentData(
            SavingFundPayment.builder()
                .amount(RETURNED_CASH.negate())
                .description("Return")
                .remitterName("TULEVA TÄIENDAV KOGUMISFOND")
                .remitterIdCode("14118923")
                .remitterIban(bankAccounts.getIban(TKF100, DEPOSIT_EUR))
                .beneficiaryName("Riina Raha")
                .beneficiaryIban(REDEMPTION_IBAN)
                .externalId(fixtureExternalId())
                .endToEndId(originalPaymentId.toString().replace("-", ""))
                .build());
    paymentRepository.changeStatus(returnPaymentId, PROCESSED);
    assertNoForeignUnmatchedOutgoingReturns();
    return originalPaymentId;
  }

  private UUID reservedPaymentReadyForIssuing() {
    given(navProvider.getVerifiedNavForIssuingAndRedeeming(eq(TKF100), any()))
        .willReturn(ISSUING_NAV);
    ClockHolder.setClock(Clock.fixed(AFTER_THE_CUTOFF, UTC));
    var party = new PartyId(PartyId.Type.PERSON, REDEEMER_CODE);
    var paymentId =
        paymentRepository.savePaymentData(
            SavingFundPayment.builder()
                .amount(ISSUED_CASH)
                .description("Subscription")
                .remitterName("Riina Raha")
                .remitterIdCode(REDEEMER_CODE)
                .remitterIban(REDEMPTION_IBAN)
                .beneficiaryName("TULEVA TÄIENDAV KOGUMISFOND")
                .beneficiaryIdCode("14118923")
                .beneficiaryIban("EE442200221092874625")
                .externalId(fixtureExternalId())
                .receivedBefore(BEFORE_THE_PREVIOUS_CUTOFF)
                .build());
    paymentRepository.attachParty(paymentId, party);
    paymentRepository.changeStatus(paymentId, RECEIVED);
    paymentRepository.changeStatus(paymentId, VERIFIED);
    paymentRepository.changeStatus(paymentId, RESERVED);
    savingsFundLedger.recordPaymentReceived(LedgerRefs.from(party), ISSUED_CASH, paymentId);
    savingsFundLedger.reservePaymentForSubscription(LedgerRefs.from(party), ISSUED_CASH, paymentId);
    assertNoForeignReservedPayments();
    return paymentId;
  }

  private User onboardedRedeemerWithFundUnits() {
    given(navProvider.getDisplayNav(TKF100)).willReturn(ONE);
    var redeemer =
        userRepository.save(
            User.builder()
                .firstName("Riina")
                .lastName("Raha")
                .personalCode(REDEEMER_CODE)
                .email("riina.raha@example.com")
                .active(true)
                .build());
    onboardingRepository.saveOnboardingStatus(REDEEMER_CODE, PartyId.Type.PERSON, COMPLETED);
    holdFundUnits();
    depositFrom(REDEMPTION_IBAN, redeemer);
    return redeemer;
  }

  private void holdFundUnits() {
    var party = LedgerRefs.from(new PartyId(PartyId.Type.PERSON, REDEEMER_CODE));
    savingsFundLedger.recordPaymentReceived(party, HELD_CASH, HELD_UNITS_REFERENCE);
    savingsFundLedger.reservePaymentForSubscription(party, HELD_CASH, HELD_UNITS_REFERENCE);
    savingsFundLedger.issueFundUnitsFromReserved(
        party, HELD_CASH, HELD_UNITS, new BigDecimal("10.00000"), HELD_UNITS_REFERENCE);
    savingsFundLedger.transferToFundAccount(HELD_CASH, HELD_UNITS_REFERENCE);
  }

  private void depositFrom(String iban, User redeemer) {
    var paymentId =
        paymentRepository.savePaymentData(
            SavingFundPayment.builder()
                .amount(new BigDecimal("100.00"))
                .description("Deposit")
                .remitterName(redeemer.getFullName())
                .remitterIdCode(REDEEMER_CODE)
                .remitterIban(iban)
                .beneficiaryName("TULEVA TÄIENDAV KOGUMISFOND")
                .beneficiaryIdCode("14118923")
                .beneficiaryIban("EE442200221092874625")
                .externalId(fixtureExternalId())
                .build());
    paymentRepository.attachParty(paymentId, new PartyId(PartyId.Type.PERSON, REDEEMER_CODE));
    paymentRepository.changeStatus(paymentId, RECEIVED);
    paymentRepository.changeStatus(paymentId, VERIFIED);
    paymentRepository.changeStatus(paymentId, RESERVED);
    paymentRepository.changeStatus(paymentId, ISSUED);
    paymentRepository.changeStatus(paymentId, PROCESSED);
  }

  private String fixtureExternalId() {
    return FIXTURE_EXTERNAL_ID_PREFIX + UUID.randomUUID();
  }

  private void assertNoForeignIssuedPayments() {
    assertThat(foreignPayments(paymentRepository.findPaymentsWithStatus(ISSUED)))
        .as(
            "the subscription batch job batches every ISSUED payment in the shared test database,"
                + " so these payments left behind by another test would end up in this test's batch"
                + " and corrupt its expected total")
        .isEmpty();
  }

  private void assertNoForeignReservedPayments() {
    assertThat(foreignPayments(paymentRepository.findPaymentsWithStatus(RESERVED)))
        .as(
            "the issuing job issues every eligible RESERVED payment in the shared test database, so"
                + " these payments left behind by another test would either inflate this test's"
                + " expected totals or fail the job outright on a missing party or receivedBefore")
        .isEmpty();
  }

  private void assertNoForeignUnmatchedOutgoingReturns() {
    var outgoingReturns =
        paymentRepository
            .findUnmatchedOutgoingReturns(bankAccounts.getIban(TKF100, DEPOSIT_EUR))
            .stream()
            .filter(returnPayment -> !hasReturnLedgerEntry(returnPayment))
            .toList();
    assertThat(foreignPayments(outgoingReturns))
        .as(
            "deferred return matching scans every unmatched outgoing return in the shared test"
                + " database that it has not already reversed, so these returns left behind by"
                + " another test would end up in this test's run and corrupt its expected matched"
                + " count")
        .isEmpty();
  }

  private boolean hasReturnLedgerEntry(SavingFundPayment returnPayment) {
    return paymentRepository
        .findOriginalPaymentForReturn(returnPayment.getEndToEndId())
        .map(
            original ->
                savingsFundLedger.hasLedgerEntry(original.getId(), PAYMENT_BOUNCE_BACK)
                    || savingsFundLedger.hasLedgerEntry(original.getId(), PAYMENT_CANCELLED))
        .orElse(false);
  }

  private List<String> foreignPayments(List<SavingFundPayment> payments) {
    return payments.stream()
        .filter(payment -> !isFixturePayment(payment))
        .map(payment -> "id=%s, externalId=%s".formatted(payment.getId(), payment.getExternalId()))
        .toList();
  }

  private boolean isFixturePayment(SavingFundPayment payment) {
    return payment.getExternalId() != null
        && payment.getExternalId().startsWith(FIXTURE_EXTERNAL_ID_PREFIX);
  }

  private List<UUID> redemptionRequestIds() {
    return jdbcClient
        .sql("select id from redemption_request where party_code = :code")
        .param("code", REDEEMER_CODE)
        .query(UUID.class)
        .list();
  }

  private List<UUID> fixturePaymentIds() {
    return jdbcClient
        .sql("select id from saving_fund_payment where external_id like :prefix")
        .param("prefix", FIXTURE_EXTERNAL_ID_PREFIX + "%")
        .query(UUID.class)
        .list();
  }

  private void sweep(Runnable... deletions) {
    var failures = new ArrayList<RuntimeException>();
    for (Runnable deletion : deletions) {
      try {
        deletion.run();
      } catch (RuntimeException e) {
        failures.add(e);
      }
    }
    if (failures.isEmpty()) {
      return;
    }
    var failure = failures.getFirst();
    failures.stream().skip(1).forEach(failure::addSuppressed);
    throw failure;
  }

  private void deleteEventLogsReferencingFixturePayments() {
    var eventLogs = stream(eventLogRepository.findAll().spliterator(), false).toList();
    var fixturePaymentIds = asStrings(fixturePaymentIds());
    var storedPaymentIds = asStrings(storedPaymentIds());
    eventLogRepository.deleteAll(
        eventLogs.stream()
            .filter(eventLog -> isFixtureEventLog(eventLog, fixturePaymentIds, storedPaymentIds))
            .toList());
  }

  private boolean isFixtureEventLog(
      EventLog eventLog, Set<String> fixturePaymentIds, Set<String> storedPaymentIds) {
    var referencedPaymentIds = referencedPaymentIds(eventLog);
    if (referencedPaymentIds.isEmpty()) {
      return false;
    }
    return !disjoint(referencedPaymentIds, fixturePaymentIds)
        || disjoint(referencedPaymentIds, storedPaymentIds);
  }

  private Set<String> referencedPaymentIds(EventLog eventLog) {
    var data = eventLog.getData();
    if (data == null || !(data.get("paymentIds") instanceof Collection<?> paymentIds)) {
      return Set.of();
    }
    return paymentIds.stream().map(String::valueOf).collect(toSet());
  }

  private Set<String> asStrings(List<UUID> ids) {
    return ids.stream().map(UUID::toString).collect(toSet());
  }

  private List<UUID> storedPaymentIds() {
    return jdbcClient.sql("select id from saving_fund_payment").query(UUID.class).list();
  }

  private void deleteLedgerTransactionsThenTheRowsThatIdentifyThem() {
    deleteLedgerTransactions();
    deleteFixturePayments();
    deleteRedemptionRequests();
  }

  private void deleteLedgerTransactions() {
    var references = new ArrayList<UUID>();
    references.add(HELD_UNITS_REFERENCE);
    references.addAll(fixturePaymentIds());
    references.addAll(redemptionRequestIds());
    jdbcClient
        .sql(
            """
            delete from ledger.entry
            where transaction_id in
              (select id from ledger.transaction where external_reference in (:references))
            """)
        .param("references", references)
        .update();
    jdbcClient
        .sql("delete from ledger.transaction where external_reference in (:references)")
        .param("references", references)
        .update();
  }

  private void deleteLedgerPartyAndAccounts() {
    jdbcClient
        .sql(
            """
            delete from ledger.account
            where owner_party_id in (select id from ledger.party where owner_id = :code)
            """)
        .param("code", REDEEMER_CODE)
        .update();
    jdbcClient
        .sql("delete from ledger.party where owner_id = :code")
        .param("code", REDEEMER_CODE)
        .update();
  }

  private void deleteFixturePayments() {
    jdbcClient
        .sql("delete from saving_fund_payment where external_id like :prefix")
        .param("prefix", FIXTURE_EXTERNAL_ID_PREFIX + "%")
        .update();
  }

  private void deleteRedemptionRequests() {
    jdbcClient
        .sql("delete from redemption_request where party_code = :code")
        .param("code", REDEEMER_CODE)
        .update();
  }

  private void deleteOnboardingStatus() {
    jdbcClient
        .sql("delete from savings_fund_onboarding where code = :code and type = :type")
        .param("code", REDEEMER_CODE)
        .param("type", PartyId.Type.PERSON.name())
        .update();
  }

  private void deleteRedeemer() {
    userRepository.findByPersonalCode(REDEEMER_CODE).ifPresent(userRepository::delete);
  }

  private static class DeliberateRollback extends RuntimeException {}
}
