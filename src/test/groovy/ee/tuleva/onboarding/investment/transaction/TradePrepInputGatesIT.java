package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.report.ReportProvider.EPIS;
import static ee.tuleva.onboarding.investment.report.ReportType.R16_FORECASTED_PAYMENTS;
import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R45;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.epis.EpisReportSummary;
import ee.tuleva.onboarding.investment.epis.EpisReportSummaryRepository;
import ee.tuleva.onboarding.investment.epis.PevaRavaCycleEntity;
import ee.tuleva.onboarding.investment.epis.PevaRavaCycleRepository;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import ee.tuleva.onboarding.investment.epis.R16FlowCalculationService;
import ee.tuleva.onboarding.investment.epis.R16PhaseCalculator;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser;
import ee.tuleva.onboarding.investment.epis.parser.R45ReportParser;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.report.ReportType;
import ee.tuleva.onboarding.investment.transaction.CommandOutcome.StoppedByInputGates;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPevaRavaReports;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPositionReport;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR16;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR45;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoR45Loaded;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.StalePositionReport;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  TradePrepInputGatesIT.EpisNavProvider.class,
  TradePrepInputGates.class,
  TradePrepInputGateNotifier.class,
  R16OutflowReader.class,
  R16FlowCalculationService.class,
  R16PhaseCalculator.class,
  R45ReportService.class,
  R45ReportParser.class,
  EpisCsvParser.class,
  PevaRavaPeriodService.class,
  PublicHolidays.class,
  Target2Calendar.class,
  EstonianCalendar.class
})
class TradePrepInputGatesIT {

  @TestConfiguration
  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.investment.epis",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = REGEX,
              pattern = "ee\\.tuleva\\.onboarding\\.investment\\.epis\\.OwnFundNavProvider"))
  static class EpisNavProvider {}

  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 18);
  private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 17);
  private static final LocalDate DECEMBER_EXECUTION_CYCLE_DATE = LocalDate.of(2027, 1, 4);
  private static final LocalDate TUK75_COUNTS_FROM = LocalDate.of(2026, 12, 22);
  private static final LocalDate TUK00_COUNTS_FROM = LocalDate.of(2026, 12, 14);

  @MockitoBean FundNavQueryService fundNavQueryService;
  @MockitoBean InvestmentParameterRepository investmentParameterRepository;
  @MockitoBean OperationsNotificationService notificationService;

  @Autowired TradePrepInputGates gates;
  @Autowired R16OutflowReader r16OutflowReader;
  @Autowired R45ReportService r45Report;
  @Autowired TradePrepInputGateNotifier notifier;
  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired InvestmentReportRepository reportRepository;
  @Autowired EpisReportSummaryRepository summaryRepository;
  @Autowired PevaRavaCycleRepository cycleRepository;
  @Autowired EntityManager entityManager;

  @BeforeEach
  void startFromNoStoredReportsOrPositions() {
    summaryRepository.deleteAll();
    cycleRepository.deleteAll();
    summaryRepository.flush();
    cycleRepository.flush();
    reportRepository.deleteAll();
    fundPositionRepository.deleteAll();
    reportRepository.flush();
    fundPositionRepository.flush();
  }

  @Test
  void passesAPensionFundWhosePositionReportIsThePreviousWorkingDaysAndWhoseEpisReportsAreLoaded() {
    positionReport(TUK75, TUESDAY);
    r45(WEDNESDAY);
    r16(YearMonth.of(2026, 11), TUK75);

    assertThat(gates.failures(TUK75, WEDNESDAY)).isEmpty();
  }

  @Test
  void aFundWithNoRowsInTodaysR45IsNotStoppedForAMissingR45() {
    positionReport(TUK00, TUESDAY);
    r45(WEDNESDAY, TUK75);
    r16(YearMonth.of(2026, 11), TUK75);

    assertThat(gates.failures(TUK00, WEDNESDAY)).isEmpty();
  }

  @Test
  void aFundWithNoRowsInThisMonthsR16IsNotStoppedForAMissingR16() {
    positionReport(TUK00, TUESDAY);
    r45(WEDNESDAY);
    r16(YearMonth.of(2026, 11), TUK75);

    assertThat(gates.failures(TUK00, WEDNESDAY)).isEmpty();
  }

  @Test
  void stopsWhenThePositionReportIsOlderThanThePreviousWorkingDay() {
    positionReport(TUK75, LocalDate.of(2026, 11, 16));
    r45(WEDNESDAY);
    r16(YearMonth.of(2026, 11), TUK75);

    assertThat(gates.failures(TUK75, WEDNESDAY))
        .containsExactly(new StalePositionReport(LocalDate.of(2026, 11, 16), TUESDAY));
  }

  @Test
  void stopsWhenTheFundHasNoPositionReportAtAll() {
    positionReport(TUK00, TUESDAY);
    r45(WEDNESDAY);
    r16(YearMonth.of(2026, 11), TUK75);

    assertThat(gates.failures(TUK75, WEDNESDAY))
        .containsExactly(new MissingPositionReport(TUESDAY));
  }

  @Test
  void onMondayFridaysPositionReportIsThePreviousWorkingDays() {
    positionReport(TKF100, LocalDate.of(2026, 11, 20));

    assertThat(gates.failures(TKF100, LocalDate.of(2026, 11, 23))).isEmpty();
  }

  @Test
  void theRequiredPositionReportSkipsEstonianPublicHolidays() {
    positionReport(TKF100, LocalDate.of(2026, 2, 20));

    assertThat(gates.failures(TKF100, LocalDate.of(2026, 2, 25)))
        .containsExactly(
            new StalePositionReport(LocalDate.of(2026, 2, 20), LocalDate.of(2026, 2, 23)));
  }

  @Test
  void stopsAPensionFundWhenTheTradeDatesR45IsNotLoaded() {
    positionReport(TUV100, TUESDAY);
    r45(TUESDAY);
    r16(YearMonth.of(2026, 11), TUV100);

    assertThat(gates.failures(TUV100, WEDNESDAY))
        .containsExactly(new MissingR45(WEDNESDAY, TUESDAY));
  }

  @Test
  void stopsAPensionFundWhenNoR45HasEverBeenLoaded() {
    positionReport(TUV100, TUESDAY);
    r16(YearMonth.of(2026, 11), TUV100);

    assertThat(gates.failures(TUV100, WEDNESDAY)).containsExactly(new NoR45Loaded(WEDNESDAY));
  }

  @Test
  void tuv100IsNotAskedForR16WhileTheR45ThatCouldExcuseItIsMissing() {
    positionReport(TUV100, TUESDAY);
    r45(TUESDAY);

    assertThat(gates.failures(TUV100, WEDNESDAY))
        .containsExactly(new MissingR45(WEDNESDAY, TUESDAY));
  }

  @Test
  void tuk75IsStillAskedForR16WhileTheR45IsMissingBecauseNoR45CanExcuseIt() {
    positionReport(TUK75, TUESDAY);
    r45(TUESDAY);

    assertThat(gates.failures(TUK75, WEDNESDAY))
        .containsExactly(
            new MissingR45(WEDNESDAY, TUESDAY),
            new MissingR16(
                YearMonth.of(2026, 11), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 9)));
  }

  @Test
  void theSavingsFundNeedsNoEpisReports() {
    positionReport(TKF100, TUESDAY);

    assertThat(gates.failures(TKF100, WEDNESDAY)).isEmpty();
  }

  @Test
  void stopsTuk75FromItsCountingDateWhenTheCyclesR17AndR21AreMissing() {
    positionReport(TUK75, LocalDate.of(2026, 12, 21));
    r45(TUK75_COUNTS_FROM);

    assertThat(gates.failures(TUK75, TUK75_COUNTS_FROM))
        .containsExactly(
            new MissingPevaRavaReports(
                List.of(R17_PEVA, R21_RAVA), DECEMBER_EXECUTION_CYCLE_DATE, TUK75_COUNTS_FROM));
  }

  @Test
  void doesNotAskTuk75ForR17AndR21BeforeItsCountingDate() {
    LocalDate dayBefore = LocalDate.of(2026, 12, 21);
    positionReport(TUK75, LocalDate.of(2026, 12, 18));
    r45(dayBefore);

    assertThat(gates.failures(TUK75, dayBefore)).isEmpty();
  }

  @Test
  void stopsTuk00WhenOnlyTheCyclesR21IsMissing() {
    LocalDate monday = LocalDate.of(2026, 12, 21);
    positionReport(TUK00, LocalDate.of(2026, 12, 18));
    r45(monday);
    cycleWithReports(reportOf(R17_PEVA, LocalDate.of(2026, 12, 3)), null);

    assertThat(gates.failures(TUK00, monday))
        .containsExactly(
            new MissingPevaRavaReports(
                List.of(R21_RAVA), DECEMBER_EXECUTION_CYCLE_DATE, TUK00_COUNTS_FROM));
  }

  @Test
  void passesTuk00OnceBothOfTheCyclesReportsAreLoaded() {
    LocalDate monday = LocalDate.of(2026, 12, 21);
    positionReport(TUK00, LocalDate.of(2026, 12, 18));
    r45(monday);
    cycleWithReports(
        reportOf(R17_PEVA, LocalDate.of(2026, 12, 3)),
        reportOf(R21_RAVA, LocalDate.of(2026, 12, 4)));

    assertThat(gates.failures(TUK00, monday)).isEmpty();
  }

  @Test
  void aCycleLinkMadeAfterAnEarlierFundLoadedTheCycleIsSeenByTheNextFundInTheSameRequest() {
    LocalDate monday = LocalDate.of(2026, 12, 21);
    positionReport(TUK00, LocalDate.of(2026, 12, 18));
    r45(monday);
    cycleWithReports(reportOf(R17_PEVA, LocalDate.of(2026, 12, 3)), null);
    assertThat(gates.failures(TUK00, monday))
        .containsExactly(
            new MissingPevaRavaReports(
                List.of(R21_RAVA), DECEMBER_EXECUTION_CYCLE_DATE, TUK00_COUNTS_FROM));

    entityManager
        .createNativeQuery(
            "UPDATE peva_rava_cycle SET r21_report_id = :r21 WHERE exec_date = :exec")
        .setParameter("r21", reportOf(R21_RAVA, LocalDate.of(2026, 12, 4)))
        .setParameter("exec", DECEMBER_EXECUTION_CYCLE_DATE)
        .executeUpdate();

    assertThat(gates.failures(TUK00, monday)).isEmpty();
  }

  @Test
  void doesNotAskForR17AndR21OnceTheCycleIsDone() {
    LocalDate doneDate = LocalDate.of(2027, 1, 7);
    positionReport(TUK75, LocalDate.of(2027, 1, 6));
    r45(doneDate);

    assertThat(gates.failures(TUK75, doneDate)).isEmpty();
  }

  @Test
  void stopsWhenThisMonthsR16IsMissingInsideTheWindowTradePrepCountsIt() {
    positionReport(TUK75, TUESDAY);
    r45(WEDNESDAY);
    r16(YearMonth.of(2026, 10), TUK75);

    assertThat(gates.failures(TUK75, WEDNESDAY))
        .containsExactly(
            new MissingR16(
                YearMonth.of(2026, 11), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 9)));
  }

  @Test
  void doesNotAskForR16BeforeItsSellByDateOrAfterTheTwentieth() {
    positionReport(TUK75, LocalDate.of(2026, 11, 5));
    positionReport(TUK75, LocalDate.of(2026, 11, 20));
    r45(LocalDate.of(2026, 11, 6));
    r45(LocalDate.of(2026, 11, 23));

    assertThat(gates.failures(TUK75, LocalDate.of(2026, 11, 6))).isEmpty();
    assertThat(gates.failures(TUK75, LocalDate.of(2026, 11, 23))).isEmpty();
  }

  @Test
  void doesNotAskTuv100ForR16WhenTheTradeDatesR45AlreadyCarriesTheMonthsThirdPillarPayouts() {
    positionReport(TUV100, TUESDAY);
    ingestR45(WEDNESDAY, "RED;EE3600001707;0.90000;0;300.00;19.11.2026");

    assertThat(gates.failures(TUV100, WEDNESDAY)).isEmpty();
  }

  @Test
  void anOlderR45CannotExcuseTuv100FromThisMonthsR16WhenTheTradeDatesR45CarriesNoPayouts() {
    positionReport(TUV100, TUESDAY);
    ingestR45(LocalDate.of(2026, 11, 13), "RED;EE3600001707;0.90000;0;300.00;17.11.2026");
    ingestR45(WEDNESDAY, "SUB;EE3600109435;0.80000;0;1500.00;19.11.2026");

    assertThat(gates.failures(TUV100, WEDNESDAY))
        .containsExactly(
            new MissingR16(
                YearMonth.of(2026, 11), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 9)));
  }

  @Test
  void anIngestedR45WhoseTuv100PayoutsHaveAllSettledStillExcusesTuv100FromThisMonthsR16() {
    positionReport(TUV100, TUESDAY);
    ingestR45(WEDNESDAY, "RED;EE3600001707;0.90000;0;300.00;17.11.2026");
    r16(YearMonth.of(2026, 11), TUV100, 1000);
    givenNav(TUV100, WEDNESDAY, "2.00000");

    assertThat(gates.failures(TUV100, WEDNESDAY)).isEmpty();
    assertThat(r16OutflowReader.read(TUV100, WEDNESDAY)).isZero();
  }

  @Test
  void tradePrepCountsTheSameMonthsR16TheGateChecksEvenWhenNextMonthsIsAlreadyLoaded() {
    LocalDate thursday = LocalDate.of(2026, 11, 12);
    r16(YearMonth.of(2026, 11), TUK75, 1000);
    r16(YearMonth.of(2026, 12), TUK75, 7000);
    givenNav(TUK75, thursday, "2.00000");

    assertThat(r16OutflowReader.read(TUK75, thursday)).isEqualByComparingTo("2000");
  }

  @Test
  void aFundWhoseInputsPassIsNotStopped() {
    positionReport(TKF100, TUESDAY);
    TransactionCommand command =
        TransactionCommand.builder().id(8L).fund(TKF100).asOfDate(WEDNESDAY).build();

    assertThatCode(() -> gates.verify(command)).doesNotThrowAnyException();
  }

  @Test
  void aStoppedFundCarriesItsFailuresBackAndItsMessageSaysWhatIsMissingAndWhatToDo() {
    positionReport(TUK75, LocalDate.of(2026, 11, 16));
    r45(TUESDAY);
    r16(YearMonth.of(2026, 11), TUK75);
    TransactionCommand command =
        TransactionCommand.builder().id(7L).fund(TUK75).asOfDate(WEDNESDAY).build();
    var stop =
        new StoppedByInputGates(
            TUK75,
            WEDNESDAY,
            List.of(
                new StalePositionReport(LocalDate.of(2026, 11, 16), TUESDAY),
                new MissingR45(WEDNESDAY, TUESDAY)));

    assertThatThrownBy(() -> gates.verify(command))
        .isInstanceOfSatisfying(
            TradePrepInputGateException.class,
            stopped -> assertThat(stopped.stop()).isEqualTo(stop));

    notifier.notifyStopped(List.of(stop));
    verify(notificationService)
        .sendMessage(
            """
            🔴 TUK75 tehingute mustandit ei tehtud (2026-11-18). Käivita ettevalmistus uuesti, kui sisend on korras:
            SEB positsiooniraport on 2026-11-16, vaja 2026-11-17: küsi 2026-11-17 raport SEB-lt
            R45 2026-11-18 puudub (viimane 2026-11-17): lae R45 EPIS-ist\
            """,
            INVESTMENT);
  }

  @Test
  void aBatchStoppedByOneMissingR45SaysItOnceAndNamesFundsOnlyOnTheirOwnLines() {
    LocalDate tradeDate = LocalDate.of(2026, 12, 22);
    LocalDate previousWorkingDay = LocalDate.of(2026, 12, 21);
    List.of(TUK75, TUK00, TUV100).forEach(fund -> positionReport(fund, previousWorkingDay));
    r45(previousWorkingDay);

    notifier.notifyStopped(
        List.of(TUK75, TUK00, TUV100).stream()
            .map(fund -> new StoppedByInputGates(fund, tradeDate, gates.failures(fund, tradeDate)))
            .toList());

    verify(notificationService)
        .sendMessage(
            """
            🔴 Tehingute mustandit ei tehtud (2026-12-22): TUK75, TUK00, TUV100. Käivita ettevalmistus uuesti, kui sisend on korras:
            R45 2026-12-22 puudub (viimane 2026-12-21): lae R45 EPIS-ist
            TUK75: R17 ja R21 puuduvad (PEVA/RAVA täitmine 2027-01-04, vood arvesse alates 2026-12-22): lae EPIS-ist
            TUK00: R17 ja R21 puuduvad (PEVA/RAVA täitmine 2027-01-04, vood arvesse alates 2026-12-14): lae EPIS-ist\
            """,
            INVESTMENT);
  }

  private void givenNav(TulevaFund fund, LocalDate asOfDate, String nav) {
    given(fundNavQueryService.findLatestNavDateOnOrBefore(fund.getCode(), asOfDate))
        .willReturn(Optional.of(asOfDate.minusDays(1)));
    given(fundNavQueryService.findPublishedNavPerUnit(fund.getCode(), asOfDate.minusDays(1)))
        .willReturn(Optional.of(new BigDecimal(nav)));
  }

  private void ingestR45(LocalDate reportDate, String... rows) {
    r45Report.processAndStore(storedR45(reportDate, rows));
  }

  private Long storedR45(LocalDate reportDate, String... rows) {
    String csv =
        Stream.concat(
                Stream.of(
                    "Tehtud: %s;;;;;".formatted(reportDate.format(ofPattern("dd.MM.yyyy"))),
                    ";;;;;",
                    "Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev"),
                Stream.of(rows))
            .collect(joining("\n", "", "\n"));
    return reportRepository
        .save(
            InvestmentReport.builder()
                .provider(EPIS)
                .reportType(R45)
                .reportDate(reportDate)
                .rawData(List.of(Map.of("csv", csv)))
                .createdAt(Instant.now())
                .build())
        .getId();
  }

  private void positionReport(TulevaFund fund, LocalDate navDate) {
    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(navDate)
            .fund(fund)
            .accountType(CASH)
            .accountName("Cash account")
            .marketValue(new BigDecimal("1000.00"))
            .currency("EUR")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());
  }

  private void r45(LocalDate reportDate, TulevaFund... fundsWithRows) {
    Long reportId = storedR45(reportDate);
    for (TulevaFund fund : fundsWithRows) {
      summaryRepository.save(
          summary(
              reportId,
              R45,
              reportDate,
              fund,
              Map.of("inflowEur", 0, "outflowEur", 0, "netEur", 0)));
    }
  }

  private void r16(YearMonth paymentMonth, TulevaFund fundWithRows) {
    r16(paymentMonth, fundWithRows, 0);
  }

  private void r16(YearMonth paymentMonth, TulevaFund fundWithRows, int fondimaksedUnits) {
    Long reportId = reportOf(R16_FORECASTED_PAYMENTS, paymentMonth.atDay(3));
    summaryRepository.save(
        summary(
            reportId,
            R16_FORECASTED_PAYMENTS,
            paymentMonth.atDay(1),
            fundWithRows,
            Map.of(
                "fondimaksedUnits",
                fondimaksedUnits,
                "uhekordsedUnits",
                0,
                "paymentMonth",
                paymentMonth.toString())));
  }

  private void cycleWithReports(Long r17ReportId, Long r21ReportId) {
    cycleRepository.save(
        PevaRavaCycleEntity.builder()
            .lockDate(LocalDate.of(2026, 11, 30))
            .execDate(DECEMBER_EXECUTION_CYCLE_DATE)
            .r17ReportId(r17ReportId)
            .r21ReportId(r21ReportId)
            .build());
  }

  private Long reportOf(ReportType reportType, LocalDate reportDate) {
    return reportRepository
        .save(
            InvestmentReport.builder()
                .provider(EPIS)
                .reportType(reportType)
                .reportDate(reportDate)
                .createdAt(Instant.now())
                .build())
        .getId();
  }

  private static EpisReportSummary summary(
      Long reportId,
      ReportType reportType,
      LocalDate reportDate,
      TulevaFund fund,
      Map<String, Object> data) {
    return EpisReportSummary.builder()
        .reportId(reportId)
        .reportType(reportType)
        .reportDate(reportDate)
        .fund(fund)
        .fundIsin(fund.getIsin())
        .data(data)
        .build();
  }
}
