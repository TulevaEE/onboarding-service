package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.calendar.DomicileCalendar;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.portfolio.Provider;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.SettlementDateCalculator;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.ingest.OverdueSettlementDetector.OverdueLine;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OverdueSettlementDetectorTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 18); // Monday
  private static final LocalDate LAST_WORKING_DAY = LocalDate.of(2026, 5, 15); // Friday
  private static final UUID SENT_UUID = UUID.randomUUID();
  private static final UUID PRESENT_UUID = UUID.randomUUID();

  @Mock private PublicHolidays publicHolidays;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private ModelPortfolioAllocationRepository allocationRepository;
  @Mock private InstrumentReferenceService instrumentReferenceService;

  private SettlementDateCalculator settlementDateCalculator() {
    Target2Calendar target2Calendar = new Target2Calendar();
    return new SettlementDateCalculator(
        target2Calendar,
        new DomicileCalendar(target2Calendar),
        allocationRepository,
        instrumentReferenceService);
  }

  private OverdueSettlementDetector detector() {
    return new OverdueSettlementDetector(
        publicHolidays, settlementDateCalculator(), executionRepository);
  }

  private void givenProvider(String isin, Provider provider) {
    given(
            allocationRepository
                .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                    eq(isin), any()))
        .willReturn(
            Optional.of(ModelPortfolioAllocation.builder().isin(isin).provider(provider).build()));
  }

  @Test
  void collectOverdue_referenceDateIsReportDate_notOverdueByReportDate_butWouldBeByWallClock() {
    // SENT ETF on Tuesday 12th -> deadline Friday 15th: overdue by wall-clock (18th), not by
    // report date (15th).
    TransactionOrder sentEtf =
        order(1L, ETF, SENT, dateOnly(2026, 5, 12), TUK75, "IE000F60HVH9", SENT_UUID);
    // EXECUTED, still in the report, settling on the report date.
    TransactionOrder executed =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", PRESENT_UUID);
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(List.of(execution(2L, LAST_WORKING_DAY, "REF2")));

    List<OverdueLine> overdue =
        detector()
            .collectOverdue(
                LAST_WORKING_DAY,
                true,
                Set.of(PRESENT_UUID),
                Set.of("REF2"),
                List.of(sentEtf, executed));

    assertThat(overdue).isEmpty();
  }

  @Test
  void collectOverdue_deadlineSkipsTarget2EasterClosingDays() {
    // Easter 2026: Good Friday 2026-04-03, Easter Monday 2026-04-06. SENT ETF on Wed 2026-04-01
    // has a 3-business-day deadline of Wed 2026-04-08 on the TARGET2 calendar (Apr 2, 7, 8) —
    // not yet overdue as of Apr 8. Weekend-only arithmetic would flag it (deadline Apr 6).
    LocalDate easterWednesday = LocalDate.of(2026, 4, 8);
    TransactionOrder sentEtf =
        order(1L, ETF, SENT, dateOnly(2026, 4, 1), TUK75, "IE000F60HVH9", SENT_UUID);
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());

    List<OverdueLine> overdue =
        detector().collectOverdue(easterWednesday, true, Set.of(), Set.of(), List.of(sentEtf));

    assertThat(overdue).isEmpty();
  }

  @Test
  void collectOverdue_fundOrderedOnANonDealingDay_dealsLaterAndIsNotYetOverdue() {
    // FUND order placed ON St Patrick's Day, 5-business-day threshold. The Irish domicile calendar
    // makes it a non-dealing day, so the fund deals on Mar 18 and the deadline is 2026-03-25. As of
    // 2026-03-25 the order is therefore NOT yet overdue.
    LocalDate afterTheDeadlineWindow = LocalDate.of(2026, 3, 25); // Wednesday
    givenProvider("IE00BFG1TM61", Provider.ISHARES);
    TransactionOrder sentFund =
        order(1L, FUND, SENT, dateOnly(2026, 3, 17), TUV100, "IE00BFG1TM61", SENT_UUID);
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());

    List<OverdueLine> overdue =
        detector()
            .collectOverdue(afterTheDeadlineWindow, true, Set.of(), Set.of(), List.of(sentFund));

    assertThat(overdue).isEmpty();
  }

  @Test
  void collectOverdue_fundWithoutADomicileDealsSameDayAndIsAlreadyOverdue() {
    // Same FUND order as above, but with no resolvable domicile the calculator falls back to
    // TARGET2, on which St Patrick's Day is a normal dealing day. The deadline is therefore
    // 2026-03-24 and the order is overdue, confirming the domicile calendar is what defers it.
    LocalDate afterTheDeadlineWindow = LocalDate.of(2026, 3, 25); // Wednesday
    TransactionOrder sentFund =
        order(1L, FUND, SENT, dateOnly(2026, 3, 17), TUV100, "EE3600109443", SENT_UUID);
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());

    List<OverdueLine> overdue =
        detector()
            .collectOverdue(afterTheDeadlineWindow, true, Set.of(), Set.of(), List.of(sentFund));

    assertThat(overdue).hasSize(1);
    assertThat(overdue.get(0).order()).isEqualTo(sentFund);
    assertThat(overdue.get(0).status()).isEqualTo(SENT);
    assertThat(overdue.get(0).deadline()).isEqualTo(LocalDate.of(2026, 3, 24));
  }

  @Test
  void collectOverdue_sentOrdersWithMissingDeadlineInputs_areNotOverdue() {
    TransactionOrder noTimestamp = order(1L, ETF, SENT, null, TUK75, "IE000F60HVH9", SENT_UUID);
    TransactionOrder noInstrumentType =
        order(2L, null, SENT, dateOnly(2026, 5, 4), TUV100, "EE3600109443", PRESENT_UUID);
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());

    List<OverdueLine> overdue =
        detector()
            .collectOverdue(
                TODAY, true, Set.of(), Set.of(), List.of(noTimestamp, noInstrumentType));

    assertThat(overdue).isEmpty();
  }

  @Test
  void collectOverdue_executedOrderWithoutExecution_fallsBackToSentDeadline() {
    // Order is still present in the fresh report (clientRef match) => not yet settled.
    TransactionOrder executed =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", PRESENT_UUID);
    // No execution row => the SENT-based deadline is used as the fallback.
    given(executionRepository.findByOrderIdIn(any())).willReturn(List.of());

    List<OverdueLine> overdue =
        detector()
            .collectOverdue(TODAY, true, Set.of(PRESENT_UUID), Set.of("REF2"), List.of(executed));

    assertThat(overdue).hasSize(1);
    assertThat(overdue.get(0).order()).isEqualTo(executed);
    assertThat(overdue.get(0).status()).isEqualTo(EXECUTED);
  }

  @Test
  void collectOverdue_executedNotYetOverdue_isExcluded() {
    TransactionOrder executed =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", PRESENT_UUID);
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(List.of(execution(2L, LocalDate.of(2026, 5, 20), "REF2")));

    List<OverdueLine> overdue =
        detector().collectOverdue(TODAY, true, Set.of(), Set.of(), List.of(executed));

    assertThat(overdue).isEmpty();
  }

  @Test
  void collectOverdue_executedPresentViaOurRef_isReportedOverdue() {
    // Order has no client ref; presence in the report is detected via the execution's Our ref.
    TransactionOrder executed =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", null);
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(List.of(execution(2L, LocalDate.of(2026, 5, 13), "REF2")));

    List<OverdueLine> overdue =
        detector().collectOverdue(TODAY, true, Set.of(), Set.of("REF2"), List.of(executed));

    assertThat(overdue).hasSize(1);
    assertThat(overdue.get(0).status()).isEqualTo(EXECUTED);
  }

  @Test
  void collectOverdue_executedNullUuid_ourRefAbsentFromFreshReport_isInferredSettled() {
    // Locks the load-bearing assumption: with a fresh, parsed report, an EXECUTED order whose only
    // identifier is the execution's Our ref is inferred settled when that ref is absent from the
    // report. This relies on SEB keeping Our ref stable across daily pending reports (the
    // documented match key). If that invariant ever breaks, this order would be falsely dropped.
    TransactionOrder executed =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", null);
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(List.of(execution(2L, LocalDate.of(2026, 5, 13), "REF2")));

    List<OverdueLine> overdue =
        detector().collectOverdue(TODAY, true, Set.of(), Set.of("OTHER_REF"), List.of(executed));

    assertThat(overdue).isEmpty();
  }

  @Test
  void collectOverdue_partiallyFilledOrder_isPresentWhenAnyPieceLingersInReport() {
    // An order filled in several SEB pieces: the first piece (REF_A) has settled and is gone from
    // the report, the second (REF_B) still lingers. Presence must be detected from ANY piece, so
    // the order is still reported overdue — not inferred settled from one arbitrary piece.
    TransactionOrder executed =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", null);
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(
            List.of(
                execution(2L, LocalDate.of(2026, 5, 13), "REF_A"),
                execution(2L, LocalDate.of(2026, 5, 13), "REF_B")));

    List<OverdueLine> overdue =
        detector().collectOverdue(TODAY, true, Set.of(), Set.of("REF_B"), List.of(executed));

    assertThat(overdue).hasSize(1);
    assertThat(overdue.get(0).status()).isEqualTo(EXECUTED);
  }

  @Test
  void collectOverdue_partiallyFilledOrder_deadlineIsTheLatestPieceSettlement() {
    // Pieces settle on different dates; the order is fully settled only when the last piece
    // settles, so the deadline is the latest piece's date (2026-05-18), not an arbitrary earlier
    // one (2026-05-13). As of the report date 2026-05-18 the order is therefore not yet overdue.
    TransactionOrder executed =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", PRESENT_UUID);
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(
            List.of(
                execution(2L, LocalDate.of(2026, 5, 13), "REF_A"),
                execution(2L, LocalDate.of(2026, 5, 18), "REF_B")));

    List<OverdueLine> overdue =
        detector()
            .collectOverdue(TODAY, true, Set.of(PRESENT_UUID), Set.of("REF2"), List.of(executed));

    assertThat(overdue).isEmpty();
  }

  @Test
  void collectOverdue_notFresh_disablesSettledInference_includesOrderRegardlessOfReportContents() {
    // With a stale/unusable report (fresh=false), presence-in-report is not trusted: an executed
    // order past its deadline is always reported overdue, independent of the report's contents.
    TransactionOrder executed =
        order(2L, FUND, EXECUTED, dateOnly(2026, 5, 4), TUV100, "EE3600109443", PRESENT_UUID);
    given(executionRepository.findByOrderIdIn(any()))
        .willReturn(List.of(execution(2L, LocalDate.of(2026, 5, 13), "REF2")));

    List<OverdueLine> overdue =
        detector().collectOverdue(TODAY, false, Set.of(), Set.of(), List.of(executed));

    assertThat(overdue).hasSize(1);
    assertThat(overdue.get(0).order()).isEqualTo(executed);
    assertThat(overdue.get(0).status()).isEqualTo(EXECUTED);
    assertThat(overdue.get(0).deadline()).isEqualTo(LocalDate.of(2026, 5, 13));
  }

  @Test
  void isUsable_reportDateOlderThanPreviousWorkingDay_returnsFalse() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    InvestmentReport stale = report(LocalDate.of(2026, 5, 11));

    assertThat(detector().isUsable(stale, TODAY)).isFalse();
  }

  @Test
  void isUsable_missingAsOfDateMetadata_returnsFalse() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    // Fresh report date but no parsed header block (no asOfDate) => truncated/corrupt, not usable.
    InvestmentReport corrupt = report(TODAY, null);

    assertThat(detector().isUsable(corrupt, TODAY)).isFalse();
  }

  @Test
  void isUsable_freshReportDateWithParsedHeader_returnsTrue() {
    given(publicHolidays.previousWorkingDay(TODAY)).willReturn(LAST_WORKING_DAY);
    InvestmentReport report = report(TODAY);

    assertThat(detector().isUsable(report, TODAY)).isTrue();
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

  private static Instant dateOnly(int year, int month, int day) {
    return LocalDate.of(year, month, day).atTime(LocalTime.NOON).atZone(TALLINN).toInstant();
  }
}
