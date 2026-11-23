package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationAuditRecorder.NAV_DATE_SET;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
class SebFundConfirmationNavDateWriterIT {

  private static final String REFERENCE = "DLA0000001";
  private static final String ISIN = "IE00BKPTWY98";
  private static final LocalDate PRICING_DATE = LocalDate.of(2026, 1, 6);
  private static final Instant NOW = Instant.parse("2026-01-08T10:00:00Z");

  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private EntityManager entityManager;

  private SebFundConfirmationRepository confirmationRepository;
  private SebFundConfirmationNavDateWriter writer;

  @BeforeEach
  void setUp() {
    confirmationRepository = new SebFundConfirmationRepository(jdbcClient);
    writer =
        new SebFundConfirmationNavDateWriter(
            confirmationRepository,
            executionRepository,
            orderRepository,
            new SebFundConfirmationAuditRecorder(
                auditEventRepository, Clock.fixed(NOW, ZoneOffset.UTC)));
  }

  @Test
  void write_setsTheRecordedPricingDateOnTheExecutionWithTheConfirmationsTradeReference() {
    recordConfirmation();
    TransactionExecution execution = persistExecution(null);

    writer.write(REFERENCE);

    assertThat(reloaded(execution).getNavDate()).isEqualTo(PRICING_DATE);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(NAV_DATE_SET, REFERENCE))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getOrderId()).isEqualTo(execution.getOrderId());
              assertThat(event.getPayload())
                  .containsEntry("executionId", String.valueOf(execution.getId()))
                  .containsEntry("navDate", "2026-01-06")
                  .doesNotContainKey("previousNavDate");
            });
  }

  @Test
  void write_replacesADifferentNavDateAndRecordsTheOneItReplaced() {
    recordConfirmation();
    TransactionExecution execution = persistExecution(LocalDate.of(2026, 1, 5));

    writer.write(REFERENCE);

    assertThat(reloaded(execution).getNavDate()).isEqualTo(PRICING_DATE);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(NAV_DATE_SET, REFERENCE))
        .singleElement()
        .satisfies(
            event -> assertThat(event.getPayload()).containsEntry("previousNavDate", "2026-01-05"));
  }

  @Test
  void write_leavesAnExecutionThatAlreadyHasThePricingDateUntouched() {
    recordConfirmation();
    TransactionExecution execution = persistExecution(PRICING_DATE);
    Long version = execution.getVersion();

    writer.write(REFERENCE);

    assertThat(reloaded(execution).getVersion()).isEqualTo(version);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(NAV_DATE_SET, REFERENCE)).isEmpty();
  }

  @Test
  void write_leavesTheNavDateOfAnExecutionOfAnotherInstrumentAlone() {
    recordConfirmation();
    TransactionExecution execution = persistExecution(null, TUK75, "IE00BFG1TM61", BUY, FUND);

    writer.write(REFERENCE);

    assertThat(reloaded(execution).getNavDate()).isNull();
  }

  @Test
  void write_leavesTheNavDateOfAnExecutionOfAnotherFundAlone() {
    recordConfirmation();
    TransactionExecution execution = persistExecution(null, TUK00, ISIN, BUY, FUND);

    writer.write(REFERENCE);

    assertThat(reloaded(execution).getNavDate()).isNull();
  }

  @Test
  void write_leavesTheNavDateOfAnExecutionOnTheOtherSideAlone() {
    recordConfirmation();
    TransactionExecution execution = persistExecution(null, TUK75, ISIN, SELL, FUND);

    writer.write(REFERENCE);

    assertThat(reloaded(execution).getNavDate()).isNull();
  }

  @Test
  void write_leavesTheTradeDateOfAnEtfFillWithTheSameReferenceAlone() {
    recordConfirmation();
    LocalDate tradeDate = LocalDate.of(2026, 1, 5);
    TransactionExecution execution = persistExecution(tradeDate, TUK75, ISIN, BUY, ETF);

    writer.write(REFERENCE);

    assertThat(reloaded(execution).getNavDate()).isEqualTo(tradeDate);
  }

  @Test
  void write_leavesTheExecutionAloneWhenItsOrderCannotBeFound() {
    recordConfirmation();
    TransactionExecution execution = persistExecution(null);
    SebFundConfirmationNavDateWriter writerFindingNoOrders =
        new SebFundConfirmationNavDateWriter(
            confirmationRepository,
            executionRepository,
            mock(TransactionOrderRepository.class),
            new SebFundConfirmationAuditRecorder(
                auditEventRepository, Clock.fixed(NOW, ZoneOffset.UTC)));

    writerFindingNoOrders.write(REFERENCE);

    assertThat(reloaded(execution).getNavDate()).isNull();
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(NAV_DATE_SET, REFERENCE)).isEmpty();
  }

  @Test
  void write_waitsUntilTheTradeHasAnExecution() {
    recordConfirmation();

    writer.write(REFERENCE);

    assertThat(executionRepository.findByBrokerTransactionId(REFERENCE)).isEmpty();
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(NAV_DATE_SET, REFERENCE)).isEmpty();
  }

  @Test
  void
      writeTo_givesAnExecutionCreatedLaterThePricingDateRecordedBeforeItsTradeEnteredTheRegistry() {
    recordConfirmation();
    TransactionOrder order = persistOrder(TUK75, ISIN, BUY, FUND);
    TransactionExecution execution = executionRepository.save(execution(order, null));

    writer.writeTo(execution, order);

    assertThat(reloaded(execution).getNavDate()).isEqualTo(PRICING_DATE);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(NAV_DATE_SET, REFERENCE))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getOrderId()).isEqualTo(order.getId());
              assertThat(event.getPayload())
                  .containsEntry("executionId", String.valueOf(execution.getId()))
                  .containsEntry("navDate", "2026-01-06");
            });
  }

  @Test
  void writeTo_leavesAnExecutionWhoseTradeHasNoRecordedConfirmationWithoutANavDate() {
    TransactionOrder order = persistOrder(TUK75, ISIN, BUY, FUND);
    TransactionExecution execution = executionRepository.save(execution(order, null));

    writer.writeTo(execution, order);

    assertThat(reloaded(execution).getNavDate()).isNull();
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(NAV_DATE_SET, REFERENCE)).isEmpty();
  }

  @Test
  void writeTo_leavesAnExecutionWithoutATradeReferenceAlone() {
    recordConfirmation();
    TransactionOrder order = persistOrder(TUK75, ISIN, BUY, FUND);
    TransactionExecution execution = execution(order, null);
    execution.setBrokerTransactionId(null);
    executionRepository.save(execution);

    writer.writeTo(execution, order);

    assertThat(reloaded(execution).getNavDate()).isNull();
  }

  private void recordConfirmation() {
    confirmationRepository.insert(
        new RecordedSebFundConfirmation(
            REFERENCE,
            TUK75,
            ISIN,
            BUY,
            Instant.parse("2026-01-05T09:48:17Z"),
            PRICING_DATE,
            "seb-confirmations/DLA0000001-001.pdf"),
        NOW);
  }

  private TransactionExecution persistExecution(@Nullable LocalDate navDate) {
    return persistExecution(navDate, TUK75, ISIN, BUY, FUND);
  }

  private TransactionExecution persistExecution(
      @Nullable LocalDate navDate,
      TulevaFund fund,
      String isin,
      TransactionType side,
      InstrumentType instrumentType) {
    TransactionExecution execution =
        executionRepository.save(
            execution(persistOrder(fund, isin, side, instrumentType), navDate));
    entityManager.flush();
    entityManager.clear();
    return execution;
  }

  private TransactionOrder persistOrder(
      TulevaFund fund, String isin, TransactionType side, InstrumentType instrumentType) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(fund).createdBy("test-user").build());
    return orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(fund)
            .instrumentIsin(isin)
            .transactionType(side)
            .instrumentType(instrumentType)
            .orderQuantity(new BigDecimal("1000"))
            .orderVenue(SEB)
            .orderUuid(UUID.randomUUID())
            .build());
  }

  private static TransactionExecution execution(
      TransactionOrder order, @Nullable LocalDate navDate) {
    return TransactionExecution.builder()
        .orderId(order.getId())
        .brokerTransactionId(REFERENCE)
        .executionTimestamp(Instant.parse("2026-01-05T12:48:00Z"))
        .navDate(navDate)
        .source("SEB_OOTEL")
        .build();
  }

  private TransactionExecution reloaded(TransactionExecution execution) {
    entityManager.flush();
    entityManager.clear();
    return executionRepository.findById(execution.getId()).orElseThrow();
  }
}
