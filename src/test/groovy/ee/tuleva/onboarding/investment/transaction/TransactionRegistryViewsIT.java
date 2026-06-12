package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.PENDING;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
class TransactionRegistryViewsIT {

  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionSettlementRepository settlementRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void transactionRegistry_derivesSettledStatusWhenSettlementExists() {
    TransactionOrder order = persistOrder(EXECUTED, LocalDate.now().minusDays(5), "100");
    persistExecution(order, "100.0000");
    persistSettlement(order);
    entityManager.flush();

    Map<String, Object> row = registryRow(order);

    assertThat(row.get("order_uuid")).isEqualTo(order.getOrderUuid());
    assertThat(row.get("fund_code")).isEqualTo("TUK75");
    assertThat(row.get("instrument_isin")).isEqualTo("IE000F60HVH9");
    assertThat(row.get("order_status")).isEqualTo("EXECUTED");
    assertThat(row.get("derived_status")).isEqualTo("SETTLED");
    assertThat(row.get("execution_id")).isNotNull();
    assertThat(row.get("settlement_id")).isNotNull();
  }

  @Test
  void transactionRegistry_derivesAwaitingSettlementWhenExecutedButNotSettled() {
    TransactionOrder order = persistOrder(EXECUTED, LocalDate.now().minusDays(3), "100");
    persistExecution(order, "100.0000");
    entityManager.flush();

    Map<String, Object> row = registryRow(order);

    assertThat(row.get("derived_status")).isEqualTo("AWAITING_SETTLEMENT");
    assertThat(row.get("settlement_id")).isNull();
  }

  @Test
  void transactionRegistry_derivesAwaitingExecutionForSentOrders() {
    TransactionOrder order = persistOrder(SENT, LocalDate.now().plusDays(2), "100");
    entityManager.flush();

    Map<String, Object> row = registryRow(order);

    assertThat(row.get("derived_status")).isEqualTo("AWAITING_EXECUTION");
    assertThat(row.get("execution_id")).isNull();
  }

  @Test
  void transactionRegistry_fallsBackToOrderStatusForOtherStates() {
    TransactionOrder order = persistOrder(PENDING, LocalDate.now().plusDays(2), "100");
    entityManager.flush();

    Map<String, Object> row = registryRow(order);

    assertThat(row.get("derived_status")).isEqualTo("PENDING");
  }

  @Test
  void delayedSettlements_includesExecutedUnsettledPastDueAndExcludesSettled() {
    TransactionOrder settled = persistOrder(EXECUTED, LocalDate.now().minusDays(5), "100");
    persistExecution(settled, "100.0000");
    persistSettlement(settled);
    TransactionOrder delayed = persistOrder(EXECUTED, LocalDate.now().minusDays(3), "200");
    persistExecution(delayed, "200.0000");
    TransactionOrder notYetDue = persistOrder(EXECUTED, LocalDate.now().plusDays(1), "300");
    persistExecution(notYetDue, "300.0000");
    entityManager.flush();

    List<Map<String, Object>> rows =
        jdbcClient
            .sql("select order_id, order_uuid, expected_settlement_date from v_delayed_settlements")
            .query()
            .listOfRows();

    assertThat(rows)
        .extracting(row -> row.get("order_id"))
        .contains(delayed.getId())
        .doesNotContain(settled.getId(), notYetDue.getId());
  }

  @Test
  void overdueOrders_includesSentOrdersPastExpectedSettlementWithoutExecution() {
    TransactionOrder overdue = persistOrder(SENT, LocalDate.now().minusDays(2), "100");
    TransactionOrder sentButExecuted = persistOrder(SENT, LocalDate.now().minusDays(2), "100");
    persistExecution(sentButExecuted, "100.0000");
    TransactionOrder sentNotDue = persistOrder(SENT, LocalDate.now().plusDays(2), "100");
    TransactionOrder pending = persistOrder(PENDING, LocalDate.now().minusDays(2), "100");
    entityManager.flush();

    List<Map<String, Object>> rows =
        jdbcClient
            .sql("select order_id, order_uuid, expected_settlement_date from v_overdue_orders")
            .query()
            .listOfRows();

    assertThat(rows)
        .extracting(row -> row.get("order_id"))
        .contains(overdue.getId())
        .doesNotContain(sentButExecuted.getId(), sentNotDue.getId(), pending.getId());
  }

  @Test
  void executionAuditTrail_returnsOrderLevelEventsWithOrderContext() {
    TransactionOrder order = persistOrder(EXECUTED, LocalDate.now().minusDays(1), "100");
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .orderId(order.getId())
            .eventType("EXECUTION_MATCHED")
            .actor("system")
            .payload(Map.of("ourRef", "DLA0799512"))
            .build());
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType("UNMATCHED_SEB_TRANSACTION")
            .actor("system")
            .payload(Map.of("isin", "IE000F60HVH9"))
            .build());
    entityManager.flush();

    List<Map<String, Object>> rows =
        jdbcClient
            .sql(
                "select order_id, order_uuid, fund_code, event_type, actor"
                    + " from v_execution_audit_trail where order_id = ?")
            .param(order.getId())
            .query()
            .listOfRows();

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("order_uuid")).isEqualTo(order.getOrderUuid());
    assertThat(rows.get(0).get("fund_code")).isEqualTo("TUK75");
    assertThat(rows.get(0).get("event_type")).isEqualTo("EXECUTION_MATCHED");
    assertThat(rows.get(0).get("actor")).isEqualTo("system");
  }

  @Test
  void unmatchedSebEntries_returnsUnmatchedAuditEventsOnly() {
    TransactionOrder order = persistOrder(EXECUTED, LocalDate.now().minusDays(1), "100");
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType("UNMATCHED_SEB_TRANSACTION")
            .actor("system")
            .payload(Map.of("isin", "IE000F60HVH9", "quantity", "15007"))
            .build());
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .orderId(order.getId())
            .eventType("EXECUTION_MATCHED")
            .actor("system")
            .payload(Map.of())
            .build());
    entityManager.flush();

    List<Map<String, Object>> rows =
        jdbcClient
            .sql(
                "select audit_event_id, event_type, payload, created_at from v_unmatched_seb_entries")
            .query()
            .listOfRows();

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("event_type")).isEqualTo("UNMATCHED_SEB_TRANSACTION");
    assertThat(rows.get(0).get("payload")).isNotNull();
    assertThat(rows.get(0).get("created_at")).isNotNull();
  }

  @Test
  void depositaryReconciliation_flagsQuantityMismatchBeyondTolerance() {
    TransactionOrder matching = persistOrder(EXECUTED, LocalDate.now().minusDays(1), "100");
    persistExecution(matching, "100.0000");
    TransactionOrder mismatched = persistOrder(EXECUTED, LocalDate.now().minusDays(1), "100");
    persistExecution(mismatched, "99.0000");
    entityManager.flush();

    assertThat(reconciliationRow(matching).get("quantity_mismatch")).isEqualTo(false);
    assertThat(reconciliationRow(mismatched).get("quantity_mismatch")).isEqualTo(true);
  }

  @Test
  void depositaryReconciliation_nullOrderQuantityIsNotAMismatch() {
    TransactionOrder amountBased =
        orderRepository.save(
            orderBuilder(EXECUTED, LocalDate.now().minusDays(1))
                .orderQuantity(null)
                .orderAmount(new BigDecimal("70915.58"))
                .build());
    persistExecution(amountBased, "15007.0000");
    entityManager.flush();

    Map<String, Object> row = reconciliationRow(amountBased);

    assertThat(row.get("quantity_mismatch")).isEqualTo(false);
    assertThat((BigDecimal) row.get("order_amount")).isEqualByComparingTo("70915.58");
    assertThat((BigDecimal) row.get("total_consideration")).isEqualByComparingTo("70915.58");
  }

  private Map<String, Object> registryRow(TransactionOrder order) {
    return jdbcClient
        .sql("select * from v_transaction_registry where order_id = ?")
        .param(order.getId())
        .query()
        .singleRow();
  }

  private Map<String, Object> reconciliationRow(TransactionOrder order) {
    return jdbcClient
        .sql(
            "select order_id, order_quantity, executed_quantity, quantity_mismatch,"
                + " order_amount, total_consideration from v_depositary_reconciliation"
                + " where order_id = ?")
        .param(order.getId())
        .query()
        .singleRow();
  }

  private TransactionOrder persistOrder(
      OrderStatus status, LocalDate expectedSettlementDate, String quantity) {
    return orderRepository.save(
        orderBuilder(status, expectedSettlementDate)
            .orderQuantity(new BigDecimal(quantity))
            .build());
  }

  private TransactionOrder.TransactionOrderBuilder orderBuilder(
      OrderStatus status, LocalDate expectedSettlementDate) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test-user").build());
    return TransactionOrder.builder()
        .batch(batch)
        .fund(TUK75)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderVenue(SEB)
        .orderUuid(UUID.randomUUID())
        .orderStatus(status)
        .expectedSettlementDate(expectedSettlementDate);
  }

  private TransactionExecution persistExecution(TransactionOrder order, String executedQuantity) {
    return executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("DLA" + order.getId())
            .executionTimestamp(Instant.parse("2026-05-11T10:26:04Z"))
            .executedQuantity(new BigDecimal(executedQuantity))
            .unitPrice(new BigDecimal("4.72550000"))
            .totalConsideration(new BigDecimal("70915.58"))
            .commissionAmount(new BigDecimal("0.00"))
            .actualSettlementDate(LocalDate.now().minusDays(1))
            .source("SEB_OOTEL")
            .build());
  }

  private TransactionSettlement persistSettlement(TransactionOrder order) {
    return settlementRepository.save(
        TransactionSettlement.builder()
            .orderId(order.getId())
            .settledAt(Instant.parse("2026-05-13T07:00:00Z"))
            .reportDate(LocalDate.now().minusDays(1))
            .build());
  }
}
