package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebPendingTransactionReconciliationBackfillJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);
  private static final int BACKFILL_DAYS = 365;

  @Spy private Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  @Mock private InvestmentReportService reportService;
  @Mock private SebPendingTransactionReconciliationService reconciliationService;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private TransactionSettlementRepository settlementRepository;
  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private SebPendingTransactionReconciliationBackfillJob job;

  @Test
  void run_replaysEveryStoredReportOldestFirstAndNeverAsALiveRun() {
    InvestmentReport day27 = report(TODAY.minusDays(27));
    InvestmentReport day10 = report(TODAY.minusDays(10));
    InvestmentReport day0 = report(TODAY);
    givenStoredReports(Map.of(27, day27, 10, day10, 0, day0));

    job.run();

    InOrder inOrder = inOrder(reconciliationService);
    inOrder.verify(reconciliationService).replay(eq(day27), any());
    inOrder.verify(reconciliationService).replay(eq(day10), any());
    inOrder.verify(reconciliationService).replay(eq(day0), any());
    verify(reconciliationService, never()).reconcile(any());
  }

  @Test
  void run_postsOneSummaryOfNewExecutionsSettlementsAndTheAlertsItHeldBack() {
    InvestmentReport day27 = report(TODAY.minusDays(27));
    InvestmentReport day10 = report(TODAY.minusDays(10));
    givenStoredReports(Map.of(27, day27, 10, day10));
    given(executionRepository.count()).willReturn(10L, 14L);
    given(settlementRepository.count()).willReturn(3L, 5L);
    willAnswer(
            invocation -> {
              ReconciliationAlerts alerts = invocation.getArgument(1);
              alerts.raise("held-back alert");
              return null;
            })
        .given(reconciliationService)
        .replay(any(), any());

    job.run();

    verify(notificationService)
        .sendMessage(
            """
            SEB ootel-raportite ajalugu sobitati uuesti kuupäevade järjekorras: 2 raportit, \
            2026-04-21 kuni 2026-05-08.
            Uusi täitmisi 4, uusi arveldusi 2. Hoiatusi jäeti saatmata 2, need on logis.
            Uuesti käivitamine on ohutu: juba sobitatut topelt ei lisata.""",
            INVESTMENT);
  }

  @Test
  void run_stopsAtTheFirstReportThatFailsAndSaysWhereItStopped() {
    InvestmentReport day27 = report(TODAY.minusDays(27));
    InvestmentReport day10 = report(TODAY.minusDays(10));
    InvestmentReport day3 = report(TODAY.minusDays(3));
    givenStoredReports(Map.of(27, day27, 10, day10, 3, day3));
    given(executionRepository.count()).willReturn(10L, 11L);
    given(settlementRepository.count()).willReturn(3L, 3L);
    lenient()
        .doThrow(new IllegalStateException("broken report"))
        .when(reconciliationService)
        .replay(eq(day10), any());

    job.run();

    verify(reconciliationService).replay(eq(day27), any());
    verify(reconciliationService, never()).replay(eq(day3), any());
    verify(notificationService)
        .sendMessage(
            """
            SEB ootel-raportite ajaloo uuesti sobitamine peatus raportil 2026-05-08 (põhjus logis).
            Enne seda 1 raport, 2026-04-21 kuni 2026-04-21: uusi täitmisi 1, uusi arveldusi 0, \
            hoiatusi jäeti saatmata 0.
            Paranda põhjus ja käivita uuesti: juba sobitatut topelt ei lisata.""",
            INVESTMENT,
            ERROR);
  }

  @Test
  void run_whenTheFirstStoredReportFails_saysNothingWasReplayedBeforeIt() {
    InvestmentReport day27 = report(TODAY.minusDays(27));
    givenStoredReports(Map.of(27, day27));
    given(executionRepository.count()).willReturn(10L, 10L);
    given(settlementRepository.count()).willReturn(3L, 3L);
    willThrow(new IllegalStateException("broken report"))
        .given(reconciliationService)
        .replay(eq(day27), any());

    job.run();

    verify(notificationService)
        .sendMessage(
            """
            SEB ootel-raportite ajaloo uuesti sobitamine peatus raportil 2026-04-21 (põhjus logis).
            Enne seda ei sobitatud ühtki raportit.
            Paranda põhjus ja käivita uuesti: juba sobitatut topelt ei lisata.""",
            INVESTMENT,
            ERROR);
  }

  @Test
  void run_withNoStoredReports_warnsThatNothingWasReplayed() {
    givenStoredReports(Map.of());

    job.run();

    verify(reconciliationService, never()).replay(any(), any());
    verify(notificationService)
        .sendMessage(
            "SEB ootel-raportite ajaloo uuesti sobitamine: viimase 365 päeva kohta pole ühtki"
                + " raportit, midagi ei sobitatud.",
            INVESTMENT,
            WARNING);
  }

  @Test
  void onSebPendingTransactionReconciliationBackfillRequested_triggersRun() {
    givenStoredReports(Map.of());

    job.onSebPendingTransactionReconciliationBackfillRequested();

    verify(notificationService)
        .sendMessage(
            "SEB ootel-raportite ajaloo uuesti sobitamine: viimase 365 päeva kohta pole ühtki"
                + " raportit, midagi ei sobitatud.",
            INVESTMENT,
            WARNING);
  }

  private void givenStoredReports(Map<Integer, InvestmentReport> reportsByDaysAgo) {
    for (int daysAgo = 0; daysAgo <= BACKFILL_DAYS; daysAgo++) {
      given(reportService.getReport(SEB, PENDING_TRANSACTIONS, TODAY.minusDays(daysAgo)))
          .willReturn(Optional.ofNullable(reportsByDaysAgo.get(daysAgo)));
    }
  }

  private static InvestmentReport report(LocalDate date) {
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(date)
        .build();
  }
}
