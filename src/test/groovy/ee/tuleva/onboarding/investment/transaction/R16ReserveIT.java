package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.report.ReportProvider.EPIS;
import static ee.tuleva.onboarding.investment.report.ReportType.R16_FORECASTED_PAYMENTS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.epis.EpisReportSummary;
import ee.tuleva.onboarding.investment.epis.EpisReportSummaryRepository;
import ee.tuleva.onboarding.investment.epis.R16FlowCalculationService;
import ee.tuleva.onboarding.investment.epis.R16PhaseCalculator;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser;
import ee.tuleva.onboarding.investment.epis.parser.R45ReportParser;
import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import ee.tuleva.onboarding.investment.portfolio.FundLimitRepository;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.time.ClockConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
  R16ReserveIT.EpisNavProvider.class,
  R16ReserveIT.LedgerWriting.class,
  R16OutflowReader.class,
  R16FlowCalculationService.class,
  R16PhaseCalculator.class,
  R45ReportService.class,
  R45ReportParser.class,
  EpisCsvParser.class,
  PublicHolidays.class,
  EstonianCalendar.class,
  ClockConfig.class
})
class R16ReserveIT {

  @TestConfiguration
  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.investment.epis",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = REGEX,
              pattern = "ee\\.tuleva\\.onboarding\\.investment\\.epis\\.OwnFundNavProvider"))
  static class EpisNavProvider {}

  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.ledger",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = REGEX,
              pattern =
                  "ee\\.tuleva\\.onboarding\\.ledger\\.(FundBankLedger|LedgerAccountService"
                      + "|LedgerTransactionService|UserUnitBalanceGuard"
                      + "|RegistrarCashFlowRepository)"))
  static class LedgerWriting {}

  private static final LocalDate TRADE_DAY = LocalDate.of(2026, 11, 18);
  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 11, 17);
  private static final LocalDate PAYOUT_DAY = LocalDate.of(2026, 11, 16);
  private static final CashHorizon REPORT_CASH = new CashHorizon.ThroughTheReportDate(REPORT_DATE);
  private static final String FUND_PENSION = "38888888888, Fondipensioni maksete lunastamine";
  private static final String ONE_OFF = "38888888888, Ühekordsete maksete osakute lunastamine";

  @MockitoBean FundNavQueryService fundNavQueryService;
  @MockitoBean OperationsNotificationService notificationService;

  @Autowired R16OutflowReader reader;
  @Autowired FundBankLedger fundBankLedger;
  @Autowired FundLimitRepository fundLimitRepository;
  @Autowired InvestmentReportRepository reportRepository;
  @Autowired EpisReportSummaryRepository summaryRepository;
  @Autowired EntityManager entityManager;

  @BeforeEach
  void navOfTwo() {
    given(fundNavQueryService.findLatestNavDateOnOrBefore(TUK75.getCode(), TRADE_DAY))
        .willReturn(Optional.of(REPORT_DATE));
    given(fundNavQueryService.findPublishedNavPerUnit(TUK75.getCode(), REPORT_DATE))
        .willReturn(Optional.of(new BigDecimal("2")));
  }

  @Test
  void anR16WithinTheGapBetweenTheSoftAndHardReserveReservesNothing() {
    reserves("1000000", "500000");
    r16(100000, 50000);

    assertThat(reader.read(TUK75, TRADE_DAY, REPORT_CASH).reserved()).isEqualByComparingTo("0");
  }

  @Test
  void anR16BeyondTheGapReservesOnlyWhatWouldTakeCashBelowTheHardReserve() {
    reserves("1000000", "500000");
    r16(300000, 100000);

    assertThat(reader.read(TUK75, TRADE_DAY, REPORT_CASH).reserved())
        .isEqualByComparingTo("300000");
  }

  @Test
  void onceTheFundPensionAndOneOffTransfersAreBookedNothingIsReserved() {
    reserves("1000000", "500000");
    r16(300000, 100000);
    payout("600000.00", PAYOUT_DAY, FUND_PENSION);
    payout("200000.00", PAYOUT_DAY, ONE_OFF);

    assertThat(reader.read(TUK75, TRADE_DAY, REPORT_CASH).reserved()).isEqualByComparingTo("0");
  }

  @Test
  void withoutAHardReserveTheWholeR16IsReserved() {
    reserves("1000000", null);
    r16(100000, 50000);

    assertThat(reader.read(TUK75, TRADE_DAY, REPORT_CASH).reserved())
        .isEqualByComparingTo("300000");
  }

  @Test
  void aTypeBookedAfterTheReportDateStillCountsOnTheReportCashButNotOnTodaysBalance() {
    reserves("1000000", "500000");
    r16(300000, 100000);
    UUID inTheBalance = payout("600000.00", TRADE_DAY, FUND_PENSION);

    assertThat(reader.read(TUK75, TRADE_DAY, REPORT_CASH).reserved())
        .isEqualByComparingTo("300000");
    assertThat(reader.read(TUK75, TRADE_DAY, balanceHolding(inTheBalance)).reserved())
        .isEqualByComparingTo("0");
  }

  @Test
  void aPayoutBookedTodayFromAReportProcessedAfterTheBalancesIsNotYetPaidForTheBalance() {
    reserves("1000000", "500000");
    r16(300000, 100000);
    payout("600000.00", TRADE_DAY, FUND_PENSION);

    assertThat(reader.read(TUK75, TRADE_DAY, balanceHolding()).reserved())
        .isEqualByComparingTo("300000");
  }

  @Test
  void aPayoutSebStillShowsAsPendingIsNotYetPaidForTheBalance() {
    reserves("1000000", "500000");
    r16(300000, 100000);
    UUID pending = payout("600000.00", TRADE_DAY, FUND_PENSION);

    assertThat(
            reader
                .read(
                    TUK75,
                    TRADE_DAY,
                    new NettingWindow(
                        TUK75, REPORT_DATE, TRADE_DAY, Set.of(pending), Set.of(pending)))
                .reserved())
        .isEqualByComparingTo("300000");
  }

  private static NettingWindow balanceHolding(UUID... bookedToday) {
    return new NettingWindow(TUK75, REPORT_DATE, TRADE_DAY, Set.of(), Set.of(bookedToday));
  }

  @Test
  void aTypeWithNoForecastUnitsIsAlreadyDoneAndAPayoutBeforeTheSellByDayDoesNotCount() {
    reserves("1000000", "500000");
    r16(0, 400000);
    payout("800000.00", LocalDate.of(2026, 11, 6), ONE_OFF);

    var reserve = reader.read(TUK75, TRADE_DAY, REPORT_CASH);

    assertThat(reserve.reserved()).isEqualByComparingTo("300000");
  }

  @Test
  void theSavingsFundHasNoR16() {
    assertThat(reader.read(TKF100, TRADE_DAY, REPORT_CASH)).isEqualTo(R16Reserve.NONE);
  }

  @Test
  void noR16ThisMonthReservesNothing() {
    reserves("1000000", "500000");

    assertThat(reader.read(TUK75, TRADE_DAY, REPORT_CASH).reserved()).isEqualByComparingTo("0");
  }

  private void reserves(String soft, @Nullable String hard) {
    fundLimitRepository.save(
        FundLimit.builder()
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .fund(TUK75)
            .reserveSoft(new BigDecimal(soft))
            .reserveHard(hard == null ? null : new BigDecimal(hard))
            .minTransaction(new BigDecimal("5000"))
            .build());
  }

  private void r16(int fondimaksedUnits, int uhekordsedUnits) {
    Long reportId =
        reportRepository
            .save(
                InvestmentReport.builder()
                    .provider(EPIS)
                    .reportType(R16_FORECASTED_PAYMENTS)
                    .reportDate(LocalDate.of(2026, 11, 3))
                    .rawData(List.of())
                    .metadata(Map.of())
                    .createdAt(Instant.now())
                    .build())
            .getId();
    summaryRepository.save(
        EpisReportSummary.builder()
            .reportId(reportId)
            .reportType(R16_FORECASTED_PAYMENTS)
            .reportDate(LocalDate.of(2026, 11, 1))
            .fund(TUK75)
            .fundIsin(TUK75.getIsin())
            .data(
                Map.of(
                    "fondimaksedUnits",
                    fondimaksedUnits,
                    "uhekordsedUnits",
                    uhekordsedUnits,
                    "paymentMonth",
                    "2026-11"))
            .build());
    entityManager.flush();
  }

  private UUID payout(String amount, LocalDate day, String remittance) {
    UUID externalReference = randomUUID();
    fundBankLedger.recordRegistrarPayout(
        TUK75, new BigDecimal(amount).negate(), externalReference, day, remittance);
    entityManager.flush();
    return externalReference;
  }
}
