package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.calendar.DomicileCalendar;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.SettlementDateCalculator;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.ingest.UnmatchedPendingTransactionFinder.InconsistentMatchedRow;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementCheckJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 18); // Monday
  private static final LocalDate LAST_WORKING_DAY = LocalDate.of(2026, 5, 15); // Friday
  private static final UUID SENT_UUID = UUID.randomUUID();
  private static final UUID PRESENT_UUID = UUID.randomUUID();
  private static final UUID SETTLED_UUID = UUID.randomUUID();

  @Mock private PublicHolidays publicHolidays;
  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private InvestmentReportService reportService;
  @Mock private SebPendingTransactionExtractor extractor;
  @Mock private UnmatchedPendingTransactionFinder unmatchedFinder;
  @Mock private SebClientNameToFundResolver fundResolver;
  @Mock private OperationsNotificationService notificationService;
  @Mock private ModelPortfolioAllocationRepository allocationRepository;
  @Mock private InstrumentReferenceService instrumentReferenceService;

  private final Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  private SettlementDateCalculator settlementDateCalculator() {
    Target2Calendar target2Calendar = new Target2Calendar();
    return new SettlementDateCalculator(
        target2Calendar,
        new DomicileCalendar(target2Calendar),
        allocationRepository,
        instrumentReferenceService);
  }

  private SettlementCheckJob job() {
    return job(clock);
  }

  private SettlementCheckJob job(Clock clock) {
    OverdueSettlementDetector detector =
        new OverdueSettlementDetector(
            publicHolidays, settlementDateCalculator(), executionRepository);
    return new SettlementCheckJob(
        clock,
        orderRepository,
        reportService,
        extractor,
        unmatchedFinder,
        fundResolver,
        notificationService,
        detector);
  }

  @Test
  void run_groupsOverdueAndUnmatchedPerFund_excludesSettled_writesNothing() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    InvestmentReport report = report(TODAY);
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.of(report));
    // Latest report contains the present EXECUTED order's clientRef but not the settled one's.
    given(extractor.extract(report)).willReturn(List.of(rowWithClientRef(PRESENT_UUID)));

    TransactionOrder sentEtf =
        order(1L, ETF, SENT, dateOnly(2026, 5, 11), TulevaFund.TUK75, "IE000F60HVH9", SENT_UUID);
    TransactionOrder executedPresent =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", PRESENT_UUID);
    TransactionOrder executedSettled =
        order(3L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUK00, "EE3600109443", SETTLED_UUID);
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of(sentEtf, executedPresent, executedSettled));
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(
            List.of(
                execution(2L, LocalDate.of(2026, 5, 13), "REF2"),
                execution(3L, LocalDate.of(2026, 5, 13), "REF3")));

    given(unmatchedFinder.collectUnmatched(report))
        .willReturn(List.of(unmatchedRow("Tuleva Täiendav Kogumisfond")));
    given(fundResolver.resolve("Tuleva Täiendav Kogumisfond")).willReturn(Optional.of(TKF100));

    job().run();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    String message = captor.getValue();
    assertThat(message)
        .contains("TUK75")
        .contains("[SAADETUD, täitmist pole] Order 1")
        .contains("TUV100")
        .contains("[TÄIDETUD, arveldus hilinenud] Order 2")
        .contains("TKF100")
        .contains("Matchimata tehingud (1)")
        .doesNotContain("Order 3");

    verify(orderRepository, never()).save(any());
    verify(executionRepository, never()).save(any());
  }

  @Test
  void run_noOverdueNoUnmatched_freshReport_sendsNothing() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    InvestmentReport report = report(TODAY);
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.of(report));
    given(extractor.extract(report)).willReturn(List.of());
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of());
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());
    given(unmatchedFinder.collectUnmatched(report)).willReturn(List.of());

    job().run();

    verifyNoInteractions(notificationService);
  }

  @Test
  void run_staleReport_disablesSettledInference_reportsExecutedOverdueWithWarning() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    // Latest report is older than the last working day => stale.
    InvestmentReport stale = report(LocalDate.of(2026, 5, 11));
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.of(stale));

    TransactionOrder executed =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", PRESENT_UUID);
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of(executed));
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(List.of(execution(2L, LocalDate.of(2026, 5, 13), "REF2")));

    job().run();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    assertThat(captor.getValue())
        .contains("raport puudub või on aegunud")
        .contains("[TÄIDETUD, arveldus hilinenud] Order 2");
    // Stale report => unmatched finder is not consulted.
    verifyNoInteractions(unmatchedFinder);
  }

  @Test
  void run_freshButCorruptReport_disablesSettledInference_reportsExecutedOverdueWithWarning() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    // Fresh report date but no parsed header block (no asOfDate) => truncated/corrupt, not usable.
    InvestmentReport corrupt = report(TODAY, null);
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS))
        .willReturn(Optional.of(corrupt));

    TransactionOrder executed =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", PRESENT_UUID);
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of(executed));
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(List.of(execution(2L, LocalDate.of(2026, 5, 13), "REF2")));

    job().run();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    assertThat(captor.getValue())
        .contains("raport puudub või on aegunud")
        .contains("[TÄIDETUD, arveldus hilinenud] Order 2");
    // Corrupt report => the executed order is NOT inferred settled, and unmatched finder is
    // skipped.
    verifyNoInteractions(unmatchedFinder);
  }

  @Test
  void run_staleReport_emptyRegistry_sendsNothing() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    // Stale report, but nothing in the transaction registry to settle => no alert.
    InvestmentReport stale = report(LocalDate.of(2026, 5, 11));
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.of(stale));
    given(extractor.extract(stale)).willReturn(List.of());
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of());
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());

    job().run();

    verifyNoInteractions(notificationService);
  }

  @Test
  void run_noReport_emptyRegistry_sendsNothing() {
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.empty());
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of());
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());

    job().run();

    verifyNoInteractions(notificationService);
  }

  @Test
  void run_noReport_fallsBackToWallClockForOverdueCheck() {
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.empty());

    // Deadline Friday 15th is not overdue by report date semantics, but with no report the job
    // falls back to wall-clock (Monday 18th) and flags it.
    TransactionOrder sentEtf =
        order(1L, ETF, SENT, dateOnly(2026, 5, 12), TulevaFund.TUK75, "IE000F60HVH9", SENT_UUID);
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of(sentEtf));
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());

    job().run();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    assertThat(captor.getValue()).contains("[SAADETUD, täitmist pole] Order 1");
  }

  @Test
  void onOverdueSettlementRequested_triggersRun() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    InvestmentReport report = report(TODAY);
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.of(report));
    given(extractor.extract(report)).willReturn(List.of());
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of());
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());
    given(unmatchedFinder.collectUnmatched(report)).willReturn(List.of());

    job().onOverdueSettlementRequested();

    verifyNoInteractions(notificationService);
  }

  @Test
  void run_noReport_disablesInferenceAndWarns() {
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.empty());

    TransactionOrder sentEtf =
        order(1L, ETF, SENT, dateOnly(2026, 5, 11), TulevaFund.TUK75, "IE000F60HVH9", SENT_UUID);
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of(sentEtf));
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());

    job().run();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    assertThat(captor.getValue())
        .contains("raport puudub või on aegunud")
        .contains("[SAADETUD, täitmist pole] Order 1");
    // No fresh report => neither the extractor nor the unmatched finder is consulted.
    verifyNoInteractions(extractor, unmatchedFinder);
  }

  @Test
  void run_unmatchedRowWithUnresolvedFund_appearsUnderUnknownBlock() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    InvestmentReport report = report(TODAY);
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.of(report));
    given(extractor.extract(report)).willReturn(List.of());
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of());
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());
    given(unmatchedFinder.collectUnmatched(report))
        .willReturn(List.of(unmatchedRowNoTradeDate("Mystery Bank Fund")));
    given(fundResolver.resolve("Mystery Bank Fund")).willReturn(Optional.empty());

    job().run();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    assertThat(captor.getValue())
        .contains("Tundmatu fond / lahendamata")
        .contains("Matchimata tehingud (1)")
        .contains("kauplemiskuupäev: null");
  }

  @Test
  void run_overdueExecutedWithNullTimestamp_rendersQuestionMark() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    InvestmentReport report = report(TODAY);
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.of(report));
    // Order is still present in the fresh report (clientRef match) => reported as overdue.
    given(extractor.extract(report)).willReturn(List.of(rowWithClientRef(PRESENT_UUID)));
    TransactionOrder executed =
        order(2L, FUND, EXECUTED, null, TUV100, "EE3600109443", PRESENT_UUID);
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of(executed));
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(List.of(execution(2L, LocalDate.of(2026, 5, 13), "REF2")));
    given(unmatchedFinder.collectUnmatched(report)).willReturn(List.of());

    job().run();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    assertThat(captor.getValue())
        .contains("[TÄIDETUD, arveldus hilinenud] Order 2")
        .contains("saadetud: ?");
  }

  @Test
  void run_inconsistentMatchedRow_appearsInDedicatedDigestSection() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    InvestmentReport report = report(TODAY);
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.of(report));
    given(extractor.extract(report)).willReturn(List.of());
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of());
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());
    given(unmatchedFinder.collectUnmatched(report)).willReturn(List.of());

    TransactionOrder order =
        order(5L, ETF, EXECUTED, dateOnly(2026, 5, 4), TUV100, "IE000F60HVH9", PRESENT_UUID);
    InconsistentMatchedRow inconsistentRow =
        new InconsistentMatchedRow(rowWithClientRef(PRESENT_UUID), order, "ISIN_SIDE_MISMATCH");
    given(unmatchedFinder.collectInconsistent(report)).willReturn(List.of(inconsistentRow));

    job().run();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    assertThat(captor.getValue())
        .contains("TUV100")
        .contains("Ebakõlalised vastavused (1)")
        .contains("ISIN_SIDE_MISMATCH")
        .contains("Order 5");
  }

  @Test
  void run_consistentReport_noInconsistentSection_sendsNothing() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    InvestmentReport report = report(TODAY);
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS)).willReturn(Optional.of(report));
    given(extractor.extract(report)).willReturn(List.of());
    given(orderRepository.findByOrderStatusInAndOrderTimestampSince(any(), any()))
        .willReturn(List.of());
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());
    given(unmatchedFinder.collectUnmatched(report)).willReturn(List.of());
    given(unmatchedFinder.collectInconsistent(report)).willReturn(List.of());

    job().run();

    verifyNoInteractions(notificationService);
  }

  private static SebPendingTransactionRow unmatchedRowNoTradeDate(String clientName) {
    return new SebPendingTransactionRow(
        UUID.randomUUID(),
        "DLA0799512",
        "IE000F60HVH9",
        new BigDecimal("15007"),
        new BigDecimal("4.7255"),
        new BigDecimal("70915.58"),
        BigDecimal.ZERO,
        new BigDecimal("70915.58"),
        BUY,
        null,
        LocalDate.of(2026, 5, 13),
        clientName,
        "VP68168",
        "ICAV Amundi MSCI USA Screened UCITS ETF");
  }

  private static InvestmentReport report(LocalDate reportDate) {
    return report(reportDate, reportDate.toString());
  }

  private static InvestmentReport report(LocalDate reportDate, String asOfDate) {
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(reportDate)
        .rawData(List.of())
        .metadata(asOfDate == null ? Map.of() : Map.of("asOfDate", asOfDate))
        .build();
  }

  private static TransactionExecution execution(
      long orderId, LocalDate scheduledSettlementDate, String brokerTransactionId) {
    return TransactionExecution.builder()
        .orderId(orderId)
        .scheduledSettlementDate(scheduledSettlementDate)
        .brokerTransactionId(brokerTransactionId)
        .source("SEB_OOTEL")
        .build();
  }

  private static TransactionOrder order(
      long id,
      InstrumentType instrumentType,
      OrderStatus status,
      Instant orderTimestamp,
      TulevaFund fund,
      String isin,
      UUID orderUuid) {
    return TransactionOrder.builder()
        .id(id)
        .fund(fund)
        .instrumentIsin(isin)
        .instrumentType(instrumentType)
        .transactionType(BUY)
        .orderStatus(status)
        .orderVenue(OrderVenue.SEB)
        .orderTimestamp(orderTimestamp)
        .orderUuid(orderUuid)
        .build();
  }

  private static SebPendingTransactionRow rowWithClientRef(UUID clientRef) {
    return new SebPendingTransactionRow(
        clientRef,
        "REF2",
        "EE3600109443",
        new BigDecimal("100"),
        BigDecimal.ONE,
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.TEN,
        BUY,
        Instant.parse("2026-05-04T10:00:00Z"),
        LocalDate.of(2026, 5, 6),
        "Tuleva Maailma Aktsiate Pensionifond",
        "VP1",
        "Some fund");
  }

  private static SebPendingTransactionRow unmatchedRow(String clientName) {
    return new SebPendingTransactionRow(
        UUID.randomUUID(),
        "DLA0799512",
        "IE000F60HVH9",
        new BigDecimal("15007"),
        new BigDecimal("4.7255"),
        new BigDecimal("70915.58"),
        BigDecimal.ZERO,
        new BigDecimal("70915.58"),
        BUY,
        Instant.parse("2026-05-11T10:26:04Z"),
        LocalDate.of(2026, 5, 13),
        clientName,
        "VP68168",
        "ICAV Amundi MSCI USA Screened UCITS ETF");
  }

  private static Instant dateOnly(int year, int month, int day) {
    return LocalDate.of(year, month, day).atTime(LocalTime.NOON).atZone(TALLINN).toInstant();
  }
}
