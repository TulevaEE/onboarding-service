package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebPriceVsNavCheckListenerTest {

  private static final UUID CLIENT_REF = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
  private static final String OUR_REF = "DLA0799512";

  @Mock private InvestmentReportService reportService;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private TransactionOrderRepository orderRepository;
  @Mock private SebPriceVsNavCheckService checkService;

  private final SebPendingTransactionExtractor extractor = new SebPendingTransactionExtractor();

  private SebPriceVsNavCheckListener newListener() {
    return new SebPriceVsNavCheckListener(
        reportService, extractor, executionRepository, orderRepository, checkService);
  }

  @Test
  void onReportImportCompleted_simpleMatchedRow_runsCheck() {
    LocalDate reportDate = LocalDate.of(2026, 5, 13);
    InvestmentReport report = reportWithSingleRow(CLIENT_REF, OUR_REF, reportDate);
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, reportDate))
        .willReturn(Optional.of(report));

    TransactionOrder order = sampleOrder(CLIENT_REF);
    TransactionExecution execution = sampleExecution(order.getId());
    given(executionRepository.findByBrokerTransactionId(OUR_REF))
        .willReturn(Optional.of(execution));
    given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

    newListener()
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, reportDate, 1));

    verify(checkService).check(execution, order);
  }

  @Test
  void onReportImportCompleted_fuzzyMatchedRow_runsCheck() {
    // Row whose clientRef does NOT match the order's orderUuid — the M3 complex matcher
    // would have linked the execution to the order. The listener must still NAV-check it,
    // discovering the linkage via brokerTransactionId.
    LocalDate reportDate = LocalDate.of(2026, 5, 13);
    UUID rowClientRef = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID orderUuidDifferent = UUID.fromString("22222222-2222-2222-2222-222222222222");
    InvestmentReport report = reportWithSingleRow(rowClientRef, OUR_REF, reportDate);
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, reportDate))
        .willReturn(Optional.of(report));

    TransactionOrder order = sampleOrder(orderUuidDifferent);
    TransactionExecution execution = sampleExecution(order.getId());
    given(executionRepository.findByBrokerTransactionId(OUR_REF))
        .willReturn(Optional.of(execution));
    given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

    newListener()
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, reportDate, 1));

    verify(checkService).check(execution, order);
  }

  @Test
  void onReportImportCompleted_swedbank_ignored() {
    newListener()
        .onReportImportCompleted(
            new ReportImportCompleted(
                SWEDBANK, PENDING_TRANSACTIONS, LocalDate.of(2026, 5, 13), 1));

    verifyNoInteractions(reportService, executionRepository, orderRepository, checkService);
  }

  @Test
  void onReportImportCompleted_sebPositions_ignored() {
    newListener()
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, POSITIONS, LocalDate.of(2026, 5, 13), 1));

    verifyNoInteractions(reportService, executionRepository, orderRepository, checkService);
  }

  @Test
  void onReportImportCompleted_reportMissing_doesNothing() {
    LocalDate reportDate = LocalDate.of(2026, 5, 13);
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, reportDate))
        .willReturn(Optional.empty());

    newListener()
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, reportDate, 1));

    verify(checkService, never()).check(any(), any());
  }

  @Test
  void onReportImportCompleted_unmatchedRow_skipsCheck() {
    LocalDate reportDate = LocalDate.of(2026, 5, 13);
    InvestmentReport report = reportWithSingleRow(CLIENT_REF, OUR_REF, reportDate);
    given(reportService.getReport(SEB, PENDING_TRANSACTIONS, reportDate))
        .willReturn(Optional.of(report));
    given(executionRepository.findByBrokerTransactionId(OUR_REF)).willReturn(Optional.empty());

    newListener()
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, reportDate, 1));

    verify(checkService, never()).check(any(), any());
    verifyNoInteractions(orderRepository);
  }

  private static TransactionOrder sampleOrder(UUID orderUuid) {
    return TransactionOrder.builder()
        .id(123L)
        .fund(TKF100)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderVenue(OrderVenue.SEB)
        .orderUuid(orderUuid)
        .orderStatus(EXECUTED)
        .build();
  }

  private static TransactionExecution sampleExecution(Long orderId) {
    return TransactionExecution.builder()
        .id(999L)
        .orderId(orderId)
        .source("SEB_OOTEL")
        .brokerTransactionId(OUR_REF)
        .unitPrice(new BigDecimal("4.7255"))
        .executionTimestamp(Instant.parse("2026-05-11T10:26:04Z"))
        .build();
  }

  private static InvestmentReport reportWithSingleRow(
      UUID clientRef, String ourRef, LocalDate reportDate) {
    Map<String, Object> raw = new HashMap<>();
    raw.put("ISIN", "IE000F60HVH9");
    raw.put("Price", new BigDecimal("4.7255"));
    raw.put("Total", new BigDecimal("70915.58"));
    raw.put("Account", "VP68168");
    raw.put("Our ref", ourRef);
    raw.put("Buy/Sell", "Buy");
    raw.put("Quantity", new BigDecimal("15007"));
    raw.put("Broker fee", new BigDecimal("0.00"));
    raw.put("Client ref", clientRef.toString());
    raw.put("Trade date", "2026-05-11T10:26:04Z");
    raw.put("Settlement date", "2026-05-13");
    raw.put("Settlement amount", new BigDecimal("70915.58"));
    raw.put("Client name", "Tuleva Täiendav Kogumisfond");
    raw.put("Instrument name", "ICAV Amundi MSCI USA Screened UCITS ETF");
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(reportDate)
        .rawData(List.of(raw))
        .build();
  }
}
