package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.NO_RESERVE_CONFIGURED;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_INFLOW_CREDIT;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRun;
import ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.Reviewed;
import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  CashBufferReviewServiceIT.LedgerWriting.class,
  ClockConfig.class,
  PublicHolidays.class,
  BusinessDays.class,
  InvestmentParameterRepository.class,
  FeeAccrualRepository.class,
  FeeChargedToFundPolicy.class,
  FlowWindowReader.class,
  CashBufferParameters.class,
  ChargedFeeAccruals.class,
  CashBufferReviewRepository.class,
  CashBufferReviewNotifier.class,
  CashBufferReviewService.class
})
class CashBufferReviewServiceIT {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
  private static final LocalDate FOURTH_BUSINESS_DAY_OF_OCTOBER = LocalDate.of(2026, 10, 6);
  private static final LocalDate FOURTH_BUSINESS_DAY_OF_NOVEMBER = LocalDate.of(2026, 11, 5);

  private static final String RECURRING = "Fondipensioni maksete lunastamine";
  private static final String ONE_OFF = "Ühekordsete maksete osakute lunastamine";
  private static final String INHERITANCE = "Pensionifondi pärimisel osakute lunastamine";
  private static final String FUND_SWITCH = "Vahetamise osakute lunastamine";
  private static final String SYNTHETIC_UNKNOWN_REASON = "Synthetic payout reason nobody mapped";
  private static final String SYNTHETIC_CONTRIBUTION = "Synthetic registrar contribution";

  @TestConfiguration
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

  @MockitoBean private OperationsNotificationService notificationService;

  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private FeeAccrualRepository feeAccrualRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private CashBufferReviewRepository reviewRepository;
  @Autowired private CashBufferReviewService service;

  @Test
  void recommendsBothLimitsFromTheLedgerAndCountsTheMonthsEachHasDriftedFromItsLimit() {
    fundBankLedger.recordOpeningBalance(TUK75, new BigDecimal("1000.00"), LocalDate.of(2026, 7, 1));
    contribution("900000.00", LocalDate.of(2026, 7, 10));
    payout("10000.00", LocalDate.of(2026, 7, 15), RECURRING);
    payout("20000.00", LocalDate.of(2026, 7, 20), ONE_OFF);
    payout("500000.00", LocalDate.of(2026, 7, 31), FUND_SWITCH);
    contribution("800000.00", LocalDate.of(2026, 8, 10));
    payout("12000.00", LocalDate.of(2026, 8, 17), RECURRING);
    payout("60000.00", LocalDate.of(2026, 8, 21), INHERITANCE);
    payout("5000.00", LocalDate.of(2026, 8, 24), SYNTHETIC_UNKNOWN_REASON);
    contribution("1000000.00", LocalDate.of(2026, 10, 12));
    payout("11000.00", LocalDate.of(2026, 10, 15), RECURRING);
    payout("4000.00", LocalDate.of(2026, 10, 16), ONE_OFF);
    parameter(CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS, TUK75, "2");
    parameter(CASH_BUFFER_INFLOW_CREDIT, TUK75, "0.1");
    fundLimit(TUK75, LocalDate.of(2026, 1, 1), "131000.00", "77000.00");
    dailyAccruals(TUK75, MANAGEMENT, SEPTEMBER, "100.00");
    dailyAccruals(TUK75, DEPOT, SEPTEMBER, "10.00");
    dailyAccruals(TUK75, MANAGEMENT, OCTOBER, "100.00");
    dailyAccruals(TUK75, DEPOT, OCTOBER, "10.00");

    service.reviewAllFunds(SEPTEMBER, FOURTH_BUSINESS_DAY_OF_OCTOBER);
    var outcomes = service.reviewAllFunds(OCTOBER, FOURTH_BUSINESS_DAY_OF_NOVEMBER);

    assertThat(outcomes).extracting(FundReviewOutcome::fund).containsExactly(TUK75, TUK00, TUV100);
    assertThat(outcomes.getFirst()).isInstanceOf(Reviewed.class);
    assertThat(outcomes.subList(1, 3))
        .allSatisfy(
            outcome ->
                assertThat(outcome)
                    .isInstanceOfSatisfying(
                        NotRun.class,
                        notRun -> assertThat(notRun.reason()).isEqualTo(NO_RESERVE_CONFIGURED)));

    var october = reviewRepository.findByFundAndMonth(TUK75, OCTOBER).orElseThrow();
    assertThat(october.window().firstMonth()).isEqualTo(YearMonth.of(2026, 7));
    assertThat(october.window().depth()).isEqualTo(4);
    assertThat(october.window().operatingOutflows())
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(amounts("30000", "72000", "0", "15000"));
    assertThat(october.window().inflows())
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(amounts("900000", "800000", "0", "1000000"));
    assertThat(october.window().unrecognisedPayouts()).isEqualTo(1);
    assertThat(october.window().unrecognisedOutflow()).isEqualByComparingTo("5000.00");

    var recommendation = october.recommendation();
    assertThat(recommendation.outflowAtPercentile()).isEqualByComparingTo("65700.00");
    assertThat(recommendation.inflowAtPercentile()).isEqualByComparingTo("480000.00");
    assertThat(recommendation.model().inflowCredit()).isEqualByComparingTo("0.1");
    assertThat(recommendation.model().settlementHorizonDays()).isEqualTo(4);
    assertThat(recommendation.horizonOutflowAtPercentile()).isEqualByComparingTo("28500.00");
    assertThat(recommendation.accruedFees()).isEqualByComparingTo("3100.00");
    assertThat(recommendation.recommendedHard()).isEqualByComparingTo("31600.00");
    assertThat(recommendation.recommendedSoft()).isEqualByComparingTo("31600.00");

    assertThat(october.configured().reserveSoft()).isEqualByComparingTo("131000.00");
    assertThat(october.configured().effectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(october.softDrift().divergence()).isEqualByComparingTo("-99400.00");
    assertThat(october.softDrift().consecutiveRuns()).isEqualTo(2);
    assertThat(october.softDrift().sustained()).isTrue();
    assertThat(october.hardDrift())
        .isNotNull()
        .satisfies(
            hardDrift -> {
              assertThat(hardDrift.divergence()).isEqualByComparingTo("-45400.00");
              assertThat(hardDrift.drifted()).isFalse();
              assertThat(hardDrift.consecutiveRuns()).isZero();
            });

    verify(notificationService, times(2)).sendMessage(anyString(), eq(INVESTMENT), eq(ERROR));
  }

  private void contribution(String amount, LocalDate bookingDate) {
    fundBankLedger.recordRegistrarContribution(
        TUK75, new BigDecimal(amount), randomUUID(), bookingDate, SYNTHETIC_CONTRIBUTION);
  }

  private void payout(String amount, LocalDate bookingDate, String remittance) {
    fundBankLedger.recordRegistrarPayout(
        TUK75, new BigDecimal(amount).negate(), randomUUID(), bookingDate, remittance);
  }

  private void parameter(InvestmentParameter parameter, TulevaFund fund, String value) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_parameter (effective_date, parameter_name, fund_code, numeric_value)
            VALUES (:effectiveDate, :name, :fundCode, :value)
            """)
        .param("effectiveDate", LocalDate.of(2026, 9, 1))
        .param("name", parameter.name())
        .param("fundCode", fund.name())
        .param("value", new BigDecimal(value))
        .update();
  }

  private void fundLimit(TulevaFund fund, LocalDate effectiveDate, String soft, String hard) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_limit (effective_date, fund_code, reserve_soft, reserve_hard)
            VALUES (:effectiveDate, :fundCode, :soft, :hard)
            """)
        .param("effectiveDate", effectiveDate)
        .param("fundCode", fund.name())
        .param("soft", new BigDecimal(soft))
        .param("hard", new BigDecimal(hard))
        .update();
  }

  private void dailyAccruals(TulevaFund fund, FeeType feeType, YearMonth month, String daily) {
    Stream.iterate(
            month.atDay(1), day -> !day.isAfter(month.atEndOfMonth()), day -> day.plusDays(1))
        .forEach(
            day ->
                feeAccrualRepository.save(
                    FeeAccrual.builder()
                        .fund(fund)
                        .feeType(feeType)
                        .accrualDate(day)
                        .feeMonth(month.atDay(1))
                        .baseValue(new BigDecimal("100000000"))
                        .annualRate(new BigDecimal("0.00365"))
                        .dailyAmountGross(new BigDecimal(daily))
                        .daysInYear(365)
                        .build()));
  }

  private static BigDecimal[] amounts(String... values) {
    return Stream.of(values).map(BigDecimal::new).toArray(BigDecimal[]::new);
  }
}
