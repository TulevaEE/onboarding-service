package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.BatchStatus.DRAFT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BatchConfirmationsTest {

  private static final String PORTFOLIO_MANAGER = "operator-1";
  private static final String FUND_MANAGER = "operator-7";

  @Mock private TransactionBatchRepository batchRepository;
  @Mock private TransactionAuditEventRepository auditEventRepository;

  @Spy
  private TwoConfirmationsRule rule =
      new TwoConfirmationsRule(
          new AdminTokenAuthenticator(
              new AdminTokenProperties(
                  "shared-token",
                  "ops-token",
                  Map.of(PORTFOLIO_MANAGER, "token-1", FUND_MANAGER, "token-7"))),
          FUND_MANAGER::equals);

  @Spy
  private Clock clock =
      Clock.fixed(Instant.parse("2026-11-20T09:00:00Z"), ZoneId.of("Europe/Tallinn"));

  @InjectMocks private BatchConfirmations confirmations;

  private final TransactionBatch batch =
      TransactionBatch.builder()
          .id(10L)
          .fund(TUK75)
          .status(DRAFT)
          .version(3L)
          .createdBy("system")
          .createdAt(Instant.parse("2026-11-20T08:00:00Z"))
          .metadata(Map.of())
          .build();

  @Test
  void giveFirst_recordsItOnTheBatchWithTheVersionAndTheOrderTypeChangesSoFar() {
    givenOrderTypeChanges(1L);

    confirmations.giveFirst(batch, 3L, PORTFOLIO_MANAGER);

    then(auditEventRepository)
        .should()
        .save(
            TransactionAuditEvent.builder()
                .batch(batch)
                .eventType("BATCH_FIRST_CONFIRMED")
                .actor(PORTFOLIO_MANAGER)
                .createdAt(Instant.parse("2026-11-20T09:00:00Z"))
                .payload(Map.of("actor", PORTFOLIO_MANAGER, "version", 3L, "orderTypeChanges", 1L))
                .build());
  }

  @Test
  void giveFirst_locksTheBatchBeforeCountingItsOrderTypeChanges() {
    confirmations.giveFirst(batch, 3L, PORTFOLIO_MANAGER);

    InOrder inOrder = inOrder(batchRepository, auditEventRepository);
    then(batchRepository).should(inOrder).lockAgainstConcurrentChange(batch);
    then(auditEventRepository).should(inOrder).countOrderEventsInBatch(10L, "ORDER_TYPE_CHANGED");
  }

  @Test
  void giveFirst_batchChangedSinceItWasLoaded_throwsConflictAndRecordsNothing() {
    willThrow(new ObjectOptimisticLockingFailureException(TransactionBatch.class, 10L))
        .given(batchRepository)
        .lockAgainstConcurrentChange(batch);

    assertRefused(() -> confirmations.giveFirst(batch, 3L, PORTFOLIO_MANAGER), CONFLICT);
    then(auditEventRepository).shouldHaveNoInteractions();
  }

  @Test
  void giveFirst_withTheSharedAdminToken_isForbiddenAndRecordsNothing() {
    assertRefused(() -> confirmations.giveFirst(batch, 3L, "shared-admin-token"), FORBIDDEN);
    then(batchRepository).shouldHaveNoInteractions();
    then(auditEventRepository).shouldHaveNoInteractions();
  }

  @Test
  void giveFirst_ofAVersionTheBatchMovedOnFrom_throwsConflictAndRecordsNothing() {
    assertRefused(() -> confirmations.giveFirst(batch, 2L, PORTFOLIO_MANAGER), CONFLICT);
    then(auditEventRepository).shouldHaveNoInteractions();
  }

  @Test
  void requireAllowedToSend_onAnotherOperatorsFirstConfirmation_allowsTheSender() {
    givenFirstConfirmations(firstConfirmationBy(PORTFOLIO_MANAGER, 3L, 0L));

    assertThatCode(() -> confirmations.requireAllowedToSend(batch, 3L, FUND_MANAGER))
        .doesNotThrowAnyException();
  }

  @Test
  void requireAllowedToSend_afterOnlyOrderTypeChangesSinceTheFirstConfirmation_allowsTheSender() {
    givenFirstConfirmations(firstConfirmationBy(PORTFOLIO_MANAGER, 2L, 0L));
    givenOrderTypeChanges(1L);

    assertThatCode(() -> confirmations.requireAllowedToSend(batch, 3L, FUND_MANAGER))
        .doesNotThrowAnyException();
  }

  @Test
  void requireAllowedToSend_afterAnyOtherChangeSinceTheFirstConfirmation_isForbidden() {
    givenFirstConfirmations(firstConfirmationBy(PORTFOLIO_MANAGER, 2L, 0L));

    assertRefused(() -> confirmations.requireAllowedToSend(batch, 3L, FUND_MANAGER), FORBIDDEN);
  }

  @Test
  void requireAllowedToSend_withoutAFirstConfirmation_isForbidden() {
    givenFirstConfirmations();

    assertRefused(() -> confirmations.requireAllowedToSend(batch, 3L, FUND_MANAGER), FORBIDDEN);
  }

  @Test
  void requireAllowedToSend_byAnOperatorWithoutSendAccess_isForbidden() {
    givenFirstConfirmations(firstConfirmationBy(FUND_MANAGER, 3L, 0L));

    assertRefused(
        () -> confirmations.requireAllowedToSend(batch, 3L, PORTFOLIO_MANAGER), FORBIDDEN);
  }

  @Test
  void lockForOrderTypeChange_movesTheBatchVersionUnderALock() {
    confirmations.lockForOrderTypeChange(batch);

    then(batchRepository).should().lockAgainstConcurrentConfirmation(batch);
  }

  @Test
  void lockForOrderTypeChange_batchChangedSinceItWasLoaded_throwsConflict() {
    willThrow(new ObjectOptimisticLockingFailureException(TransactionBatch.class, 10L))
        .given(batchRepository)
        .lockAgainstConcurrentConfirmation(batch);

    assertRefused(() -> confirmations.lockForOrderTypeChange(batch), CONFLICT);
  }

  @Test
  void lockForOrderTypeChange_lockNotAcquired_throwsConflict() {
    willThrow(new CannotAcquireLockException("lock timeout"))
        .given(batchRepository)
        .lockAgainstConcurrentConfirmation(batch);

    assertRefused(() -> confirmations.lockForOrderTypeChange(batch), CONFLICT);
  }

  private void givenFirstConfirmations(TransactionAuditEvent... firstConfirmations) {
    given(auditEventRepository.findByBatchIdAndEventType(10L, "BATCH_FIRST_CONFIRMED"))
        .willReturn(List.of(firstConfirmations));
  }

  private void givenOrderTypeChanges(long count) {
    given(auditEventRepository.countOrderEventsInBatch(10L, "ORDER_TYPE_CHANGED"))
        .willReturn(count);
  }

  private static TransactionAuditEvent firstConfirmationBy(
      String operator, Long version, Long orderTypeChanges) {
    return TransactionAuditEvent.builder()
        .eventType("BATCH_FIRST_CONFIRMED")
        .actor(operator)
        .createdAt(Instant.parse("2026-11-20T08:30:00Z"))
        .payload(
            Map.of("actor", operator, "version", version, "orderTypeChanges", orderTypeChanges))
        .build();
  }

  private static void assertRefused(ThrowingCallable attempt, HttpStatus status) {
    assertThatThrownBy(attempt)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
  }
}
