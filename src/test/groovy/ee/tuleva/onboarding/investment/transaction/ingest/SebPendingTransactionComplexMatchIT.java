package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
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
class SebPendingTransactionComplexMatchIT {

  private static final String ETF_ISIN = "IE00BFNM3G45";

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
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test").build());
    order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TUK75)
                .instrumentIsin(ETF_ISIN)
                .transactionType(BUY)
                .instrumentType(ETF)
                .orderQuantity(new BigDecimal("13288"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(UUID.randomUUID())
                .orderStatus(SENT)
                .build());

    report =
        reportRepository.save(
            InvestmentReport.builder()
                .provider(SEB)
                .reportType(PENDING_TRANSACTIONS)
                .reportDate(LocalDate.of(2026, 5, 13))
                .rawData(List.of(rawRowWithoutClientRef()))
                .metadata(Map.of("source", "fixture"))
                .createdAt(Instant.now())
                .build());
  }

  @Test
  void reconcile_missingClientRef_complexMatchByFundIsinSideAndQuantity() {
    reconciliationService.reconcile(report);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution execution = executionRepository.findAllByOrderId(order.getId()).getFirst();
    assertThat(execution.getExecutedQuantity()).isEqualByComparingTo("13288");
    assertThat(execution.getSource()).isEqualTo("SEB_OOTEL");

    TransactionOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getOrderStatus()).isEqualTo(EXECUTED);
  }

  private static Map<String, Object> rawRowWithoutClientRef() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("Client name", "Tuleva Maailma Aktsiate Pensionifond");
    raw.put("Account", "VP68958");
    raw.put("Our ref", "DLA0001234");
    raw.put("ISIN", ETF_ISIN);
    raw.put("Instrument name", "iShares Edge MSCI USA Quality Factor UCITS ETF");
    raw.put("Currency", "EUR");
    raw.put("Quantity", new BigDecimal("13288"));
    raw.put("Price", new BigDecimal("34.50"));
    raw.put("Settlement amount", new BigDecimal("458436.00"));
    raw.put("Broker fee", new BigDecimal("0.00"));
    raw.put("Total", new BigDecimal("458436.00"));
    raw.put("Buy/Sell", "Buy");
    raw.put("Trade date", "2026-05-11T10:26:04Z");
    raw.put("Settlement date", "2026-05-13");
    // No "Client ref" key — simulates a SEB row with missing UUID
    return raw;
  }
}
