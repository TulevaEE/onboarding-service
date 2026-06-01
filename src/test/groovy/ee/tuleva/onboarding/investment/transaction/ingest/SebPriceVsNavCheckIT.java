package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(SebPriceVsNavCheckIT.TestEventRecorder.class)
class SebPriceVsNavCheckIT {

  private static final String ISIN = "IE000F60HVH9";
  // AMUNDI_USA_SCREENED EODHD storage key (PositionPriceResolver resolves ISIN -> ticker).
  private static final String STORAGE_KEY = "USAS.PA.EODHD";
  private static final String PROVIDER = "EODHD";
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 5, 11);
  private static final Instant TRADE_INSTANT = Instant.parse("2026-05-11T10:26:04Z");
  private static final Instant PRICE_UPDATED_AT = Instant.parse("2026-05-11T18:00:00Z");

  @Autowired private SebPriceVsNavCheckService service;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private EntityManager entityManager;
  @Autowired private TestEventRecorder recorder;

  private TransactionOrder order;
  private TransactionExecution execution;

  @BeforeEach
  void seed() {
    recorder.events.clear();
    deletePriceRows();
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TKF100)
                .instrumentIsin(ISIN)
                .transactionType(BUY)
                .instrumentType(ETF)
                .orderQuantity(15007L)
                .orderVenue(OrderVenue.SEB)
                .orderUuid(UUID.randomUUID())
                .orderStatus(SENT)
                .build());
    execution =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .source("SEB_OOTEL")
                .brokerTransactionId("DLA0799512")
                .executionTimestamp(TRADE_INSTANT)
                .executedQuantity(new BigDecimal("15007"))
                .unitPrice(new BigDecimal("4.7255"))
                .totalConsideration(new BigDecimal("70915.58"))
                .actualSettlementDate(LocalDate.of(2026, 5, 13))
                .build());
  }

  @AfterEach
  void cleanPrices() {
    deletePriceRows();
  }

  @Test
  void etfWithinTolerance_noEvent() {
    insertMarketPrice(TRADE_DATE, new BigDecimal("4.7255"));

    service.check(execution, order);

    assertThat(recorder.events).isEmpty();
  }

  @Test
  void etfOutsideTolerance_emitsExecutionMismatchEvent() {
    insertMarketPrice(TRADE_DATE, new BigDecimal("4.7800"));

    service.check(execution, order);

    assertThat(recorder.events).hasSize(1).first().isInstanceOf(ExecutionMismatchEvent.class);
    ExecutionMismatchEvent ev = (ExecutionMismatchEvent) recorder.events.get(0);
    assertThat(ev.executionId()).isEqualTo(execution.getId());
    assertThat(ev.isin()).isEqualTo(ISIN);
    assertThat(ev.execPrice()).isEqualByComparingTo("4.7255");
    assertThat(ev.navPrice()).isEqualByComparingTo("4.7800");
    assertThat(ev.tradeDate()).isEqualTo(TRADE_DATE);
  }

  @Test
  void fundInstrument_noEventEvenWhenPriceMismatches() {
    order.setInstrumentType(FUND);
    orderRepository.save(order);
    entityManager.flush();
    insertMarketPrice(TRADE_DATE, new BigDecimal("5.0000"));

    service.check(execution, order);

    assertThat(recorder.events).isEmpty();
  }

  @Test
  void etfWithNoMarketPrice_emitsNavMissingEvent() {
    service.check(execution, order);

    assertThat(recorder.events).hasSize(1).first().isInstanceOf(NavMissingEvent.class);
    NavMissingEvent ev = (NavMissingEvent) recorder.events.get(0);
    assertThat(ev.executionId()).isEqualTo(execution.getId());
    assertThat(ev.isin()).isEqualTo(ISIN);
    assertThat(ev.tradeDate()).isEqualTo(TRADE_DATE);
  }

  @Test
  void etfWithPriceOnlyForEarlierDate_emitsNavMissingEvent() {
    insertMarketPrice(TRADE_DATE.minusDays(3), new BigDecimal("4.7255"));

    service.check(execution, order);

    assertThat(recorder.events).hasSize(1).first().isInstanceOf(NavMissingEvent.class);
  }

  private void insertMarketPrice(LocalDate date, BigDecimal price) {
    jdbcClient
        .sql(
            """
            INSERT INTO index_values (key, date, value, provider, updated_at)
            VALUES (:key, :date, :value, :provider, :updatedAt)
            """)
        .param("key", STORAGE_KEY)
        .param("date", date)
        .param("value", price)
        .param("provider", PROVIDER)
        .param("updatedAt", PRICE_UPDATED_AT)
        .update();
  }

  private void deletePriceRows() {
    jdbcClient.sql("DELETE FROM index_values WHERE key = :key").param("key", STORAGE_KEY).update();
  }

  public static class TestEventRecorder {
    final List<Object> events = new ArrayList<>();

    @EventListener
    void onMismatch(ExecutionMismatchEvent event) {
      events.add(event);
    }

    @EventListener
    void onMissing(NavMissingEvent event) {
      events.add(event);
    }
  }
}
