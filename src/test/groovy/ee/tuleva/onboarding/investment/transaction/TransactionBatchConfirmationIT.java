package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.BatchStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.DRAFT;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({
  TransactionAdminService.class,
  BatchConfirmations.class,
  TwoConfirmationsRule.class,
  AdminTokenAuthenticator.class,
  TransactionBatchConfirmationIT.NamedOperatorsConfig.class
})
class TransactionBatchConfirmationIT {

  private static final String PORTFOLIO_MANAGER = "operator-1";
  private static final String FUND_MANAGER = "operator-7";

  @Autowired private TransactionAdminService service;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private TransactionPreparationService preparationService;
  @MockitoBean private TransactionBatchFinalizer batchFinalizer;

  @TestConfiguration
  static class NamedOperatorsConfig {

    @Bean
    AdminTokenProperties adminTokenProperties() {
      return new AdminTokenProperties(
          "shared-token",
          "ops-token",
          Map.of(PORTFOLIO_MANAGER, "token-1", FUND_MANAGER, "token-7"));
    }

    @Bean
    OrderSendAccess orderSendAccess() {
      return FUND_MANAGER::equals;
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-11-18T09:00:00Z"), ZoneOffset.UTC);
    }
  }

  @AfterEach
  void cleanUp() {
    auditEventRepository.deleteAll();
    orderRepository.deleteAll();
    batchRepository.deleteAll();
  }

  @Test
  void thePortfolioManagersOrderTypeChangeGoesOutOnTheTwoConfirmations() {
    TransactionOrder order = draftOrderInABatchPreparedBy(PORTFOLIO_MANAGER);
    Long batchId = order.getBatch().getId();
    service.setOrderType(order.getId(), OrderType.NAV, PORTFOLIO_MANAGER);
    service.giveFirstConfirmation(batchId, currentVersion(batchId), PORTFOLIO_MANAGER);

    service.confirmAndFinalize(batchId, currentVersion(batchId), FUND_MANAGER);

    assertSentBy(FUND_MANAGER, batchId);
  }

  @Test
  void theFundManagerChangesAnOrderTypeAfterTheFirstConfirmationAndSendsWithoutANewOne() {
    TransactionOrder order = draftOrderInABatchPreparedBy(PORTFOLIO_MANAGER);
    Long batchId = order.getBatch().getId();
    service.giveFirstConfirmation(batchId, currentVersion(batchId), PORTFOLIO_MANAGER);
    service.setOrderType(order.getId(), OrderType.NAV, FUND_MANAGER);

    service.confirmAndFinalize(batchId, currentVersion(batchId), FUND_MANAGER);

    assertSentBy(FUND_MANAGER, batchId);
  }

  @Test
  void anyOtherChangeToTheBatchAfterTheFirstConfirmationStopsTheSend() {
    TransactionOrder order = draftOrderInABatchPreparedBy(PORTFOLIO_MANAGER);
    Long batchId = order.getBatch().getId();
    service.giveFirstConfirmation(batchId, currentVersion(batchId), PORTFOLIO_MANAGER);
    TransactionBatch changedOtherwise = batchRepository.findById(batchId).orElseThrow();
    changedOtherwise.setMetadata(Map.of("recalculated", true));
    batchRepository.save(changedOtherwise);

    assertRefusedAndStillADraft(
        () -> service.confirmAndFinalize(batchId, currentVersion(batchId), FUND_MANAGER),
        FORBIDDEN,
        batchId);
  }

  @Test
  void thePortfolioManagerCannotSendTheBatchEvenAfterTheFundManagersFirstConfirmation() {
    TransactionOrder order = draftOrderInABatchPreparedBy(PORTFOLIO_MANAGER);
    Long batchId = order.getBatch().getId();
    service.giveFirstConfirmation(batchId, currentVersion(batchId), FUND_MANAGER);

    assertRefusedAndStillADraft(
        () -> service.confirmAndFinalize(batchId, currentVersion(batchId), PORTFOLIO_MANAGER),
        FORBIDDEN,
        batchId);
  }

  @Test
  void aFirstConfirmationOfTheVersionBeforeAChangeIsRefusedAndNotRecorded() {
    TransactionOrder order = draftOrderInABatchPreparedBy(PORTFOLIO_MANAGER);
    Long batchId = order.getBatch().getId();
    Long reviewedVersion = currentVersion(batchId);
    service.setOrderType(order.getId(), OrderType.RISK, FUND_MANAGER);

    assertRefusedAndStillADraft(
        () -> service.giveFirstConfirmation(batchId, reviewedVersion, PORTFOLIO_MANAGER),
        CONFLICT,
        batchId);
    assertThat(auditEventRepository.findByBatchIdAndEventType(batchId, "BATCH_FIRST_CONFIRMED"))
        .isEmpty();
  }

  @Test
  void aSenderWhoReviewedTheVersionBeforeAChangeCannotSendTheChangedBatch() {
    TransactionOrder order = draftOrderInABatchPreparedBy(PORTFOLIO_MANAGER);
    Long batchId = order.getBatch().getId();
    Long reviewedVersion = currentVersion(batchId);
    service.setOrderType(order.getId(), OrderType.RISK, PORTFOLIO_MANAGER);
    service.giveFirstConfirmation(batchId, currentVersion(batchId), PORTFOLIO_MANAGER);

    assertRefusedAndStillADraft(
        () -> service.confirmAndFinalize(batchId, reviewedVersion, FUND_MANAGER),
        CONFLICT,
        batchId);
  }

  @Test
  void theFirstConfirmationsLockConflictsWhenTheBatchChangedAfterItWasLoaded() {
    TransactionOrder order = draftOrderInABatchPreparedBy(PORTFOLIO_MANAGER);
    Long batchId = order.getBatch().getId();
    TransactionTemplate changeInItsOwnTransaction = new TransactionTemplate(transactionManager);
    changeInItsOwnTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);

    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        firstConfirmation -> {
                          TransactionBatch loadedBeforeTheChange =
                              batchRepository.findById(batchId).orElseThrow();
                          changeInItsOwnTransaction.executeWithoutResult(
                              change ->
                                  service.setOrderType(order.getId(), OrderType.NAV, FUND_MANAGER));
                          batchRepository.lockAgainstConcurrentChange(loadedBeforeTheChange);
                        }))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  void anOrderTypeChangeMovesTheBatchVersionSoAConfirmationThatReadTheBatchBeforeItConflicts() {
    TransactionOrder order = draftOrderInABatchPreparedBy(PORTFOLIO_MANAGER);
    Long batchId = order.getBatch().getId();
    TransactionTemplate changeInItsOwnTransaction = new TransactionTemplate(transactionManager);
    changeInItsOwnTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);

    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        confirmation -> {
                          TransactionBatch readBeforeTheChange =
                              batchRepository.findById(batchId).orElseThrow();
                          changeInItsOwnTransaction.executeWithoutResult(
                              change ->
                                  service.setOrderType(
                                      order.getId(), OrderType.RISK, FUND_MANAGER));
                          readBeforeTheChange.setStatus(CONFIRMED);
                          batchRepository.saveAndFlush(readBeforeTheChange);
                        }))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  private void assertSentBy(String sender, Long batchId) {
    assertThat(batchRepository.findById(batchId))
        .get()
        .extracting(TransactionBatch::getStatus, TransactionBatch::getConfirmedBy)
        .containsExactly(CONFIRMED, sender);
  }

  private Long currentVersion(Long batchId) {
    return batchRepository.findById(batchId).orElseThrow().getVersion();
  }

  private void assertRefusedAndStillADraft(
      ThrowingCallable attempt, HttpStatus status, Long batchId) {
    assertThatThrownBy(attempt)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
    assertThat(batchRepository.findById(batchId))
        .get()
        .extracting(TransactionBatch::getStatus)
        .isEqualTo(DRAFT);
  }

  private TransactionOrder draftOrderInABatchPreparedBy(String operator) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy(operator).build());
    return orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUK75)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderQuantity(new BigDecimal("15007"))
            .orderVenue(SEB)
            .orderUuid(UUID.randomUUID())
            .build());
  }
}
