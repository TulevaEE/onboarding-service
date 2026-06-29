package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SebPendingTransactionReconciliationIdempotencyIT {

  private static final UUID CLIENT_REF = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
  private static final String ISIN = "IE000F60HVH9";

  @Autowired private SebPendingTransactionReconciliationService reconciliationService;
  @Autowired private InvestmentReportRepository reportRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private EntityManager entityManager;

  private TransactionOrder order;
  private InvestmentReport report;

  @BeforeEach
  void seed() {
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
                .orderQuantity(new BigDecimal("15007"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(CLIENT_REF)
                .orderStatus(SENT)
                .build());

    report =
        reportRepository.save(
            InvestmentReport.builder()
                .provider(SEB)
                .reportType(PENDING_TRANSACTIONS)
                .reportDate(LocalDate.of(2026, 5, 13))
                .rawData(List.of(rawRow()))
                .metadata(Map.of("source", "fixture"))
                .createdAt(Instant.now())
                .build());
  }

  @Test
  void reconcile_runTwice_producesExactlyOneExecution() {
    reconciliationService.reconcile(report);
    entityManager.flush();
    Long firstExecutionId = executionRepository.findAllByOrderId(order.getId()).getFirst().getId();

    reconciliationService.reconcile(report);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution after = executionRepository.findAllByOrderId(order.getId()).getFirst();
    assertThat(after.getId()).isEqualTo(firstExecutionId);

    List<TransactionExecution> allForOrder =
        executionRepository.findAll().stream()
            .filter(e -> order.getId().equals(e.getOrderId()))
            .toList();
    assertThat(allForOrder).hasSize(1);
  }

  private static Map<String, Object> rawRow() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("Client name", "Tuleva Täiendav Kogumisfond");
    raw.put("Account", "VP68168");
    raw.put("Our ref", "DLA0799512");
    raw.put("ISIN", ISIN);
    raw.put("Instrument name", "ICAV Amundi MSCI USA Screened UCITS ETF");
    raw.put("Currency", "EUR");
    raw.put("Quantity", new BigDecimal("15007"));
    raw.put("Price", new BigDecimal("4.7255"));
    raw.put("Settlement amount", new BigDecimal("70915.58"));
    raw.put("Broker fee", new BigDecimal("0.00"));
    raw.put("Total", new BigDecimal("70915.58"));
    raw.put("Buy/Sell", "Buy");
    raw.put("Client ref", CLIENT_REF.toString());
    raw.put("Trade date", "2026-05-11T10:26:04Z");
    raw.put("Settlement date", "2026-05-13");
    return raw;
  }
}
