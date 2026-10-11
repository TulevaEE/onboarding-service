package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.NO_RESERVE_CONFIGURED;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_INFLOW_CREDIT;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REGISTRAR_PAYOUT;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import ee.tuleva.onboarding.ledger.FundBankLedger.UnclassifiedEntryDetails;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
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

  private static final RecursiveComparisonConfiguration AMOUNTS_BY_VALUE =
      RecursiveComparisonConfiguration.builder()
          .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .build();

  private static final YearMonth JULY = YearMonth.of(2026, 7);
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
  private static final LocalDate FOURTH_BUSINESS_DAY_OF_OCTOBER = LocalDate.of(2026, 10, 6);
  private static final LocalDate FOURTH_BUSINESS_DAY_OF_NOVEMBER = LocalDate.of(2026, 11, 5);
  private static final LocalDate FIFTH_BUSINESS_DAY_OF_NOVEMBER = LocalDate.of(2026, 11, 6);

  private static final String RECURRING = "Fondipensioni maksete lunastamine";
  private static final String ONE_OFF = "Ühekordsete maksete osakute lunastamine";
  private static final String INHERITANCE = "Pensionifondi pärimisel osakute lunastamine";
  private static final String FUND_SWITCH = "Vahetamise osakute lunastamine";
  private static final String SYNTHETIC_UNKNOWN_REASON = "Synthetic payout reason nobody mapped";
  private static final String SYNTHETIC_CONTRIBUTION = "Synthetic registrar contribution";
  private static final String SYNTHETIC_PERSONAL_CODE = "38888888888";

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
    julyThroughOctoberOnTheLedgerWithTheirFees();
    fundLimit(TUK75, LocalDate.of(2026, 1, 1), "131000.00", "77000.00");

    service.reviewAllFunds(SEPTEMBER, FOURTH_BUSINESS_DAY_OF_OCTOBER);
    var outcomes = service.reviewAllFunds(OCTOBER, FOURTH_BUSINESS_DAY_OF_NOVEMBER);

    var expectedOctober =
        new CashBufferReview(
            TUK75,
            OCTOBER,
            FOURTH_BUSINESS_DAY_OF_NOVEMBER,
            new FlowWindow(
                List.of(
                    flows(JULY, "900000.00", "10000.00", "20000.00", "500000.00", "0.00", 0),
                    flows(AUGUST, "800000.00", "12000.00", "60000.00", "0.00", "5000.00", 1),
                    flows(SEPTEMBER, "0.00", "0.00", "0.00", "0.00", "0.00", 0),
                    flows(OCTOBER, "1000000.00", "11000.00", "4000.00", "0.00", "0.00", 0))),
            new Recommendation(
                new BufferModel(amount("0.95"), amount("0.20"), amount("0.1"), 4),
                amount("65700.00"),
                amount("480000.00"),
                amount("28500.00"),
                amount("3100.00"),
                amount("31600.00"),
                amount("31600.00")),
            new ConfiguredReserve(
                LocalDate.of(2026, 1, 1), amount("131000.00"), amount("77000.00")),
            new Drift(amount("-99400.00"), amount("50000"), true, 2, 2),
            new Drift(amount("-45400.00"), amount("50000"), false, 0, 2));
    assertThat(outcomes)
        .usingRecursiveFieldByFieldElementComparator(AMOUNTS_BY_VALUE)
        .containsExactly(
            new Reviewed(expectedOctober, 0),
            new NotRun(TUK00, NO_RESERVE_CONFIGURED, "asOf=2026-11-05"),
            new NotRun(TUV100, NO_RESERVE_CONFIGURED, "asOf=2026-11-05"));
    assertThat(reviewRepository.findByFundAndMonth(TUK75, OCTOBER))
        .get()
        .usingRecursiveComparison(AMOUNTS_BY_VALUE)
        .isEqualTo(expectedOctober);

    verify(notificationService, times(2)).sendMessage(anyString(), eq(INVESTMENT), eq(ERROR));
  }

  @Test
  void theHardLimitsRunCarriesFromOneStoredMonthToTheNextLikeTheSoftOnes() {
    julyThroughOctoberOnTheLedgerWithTheirFees();
    fundLimit(TUK75, LocalDate.of(2026, 1, 1), "131000.00", "200000.00");

    service.reviewAllFunds(SEPTEMBER, FOURTH_BUSINESS_DAY_OF_OCTOBER);
    service.reviewAllFunds(OCTOBER, FOURTH_BUSINESS_DAY_OF_NOVEMBER);

    var october = reviewRepository.findByFundAndMonth(TUK75, OCTOBER).orElseThrow();
    assertThat(october.hardDrift())
        .usingRecursiveComparison(AMOUNTS_BY_VALUE)
        .isEqualTo(new Drift(amount("-168400.00"), amount("50000"), true, 2, 2));
  }

  @Test
  void aNewlyEnteredLimitStartsItsRunAgainWhileTheLimitLeftAsItWasKeepsCounting() {
    julyThroughOctoberOnTheLedgerWithTheirFees();
    fundLimit(TUK75, LocalDate.of(2026, 1, 1), "131000.00", "200000.00");
    fundLimit(TUK75, LocalDate.of(2026, 10, 15), "131000.00", "210000.00");

    service.reviewAllFunds(SEPTEMBER, FOURTH_BUSINESS_DAY_OF_OCTOBER);
    service.reviewAllFunds(OCTOBER, FOURTH_BUSINESS_DAY_OF_NOVEMBER);

    var october = reviewRepository.findByFundAndMonth(TUK75, OCTOBER).orElseThrow();
    assertThat(List.of(october.softDrift(), october.hardDrift()))
        .usingRecursiveFieldByFieldElementComparator(AMOUNTS_BY_VALUE)
        .containsExactly(
            new Drift(amount("-99400.00"), amount("50000"), true, 2, 2),
            new Drift(amount("-178400.00"), amount("50000"), true, 1, 2));
  }

  @Test
  void aMonthNotReviewableOnItsFourthBusinessDayIsReviewedOnALaterOneAndKeepsItsDriftRun() {
    julyThroughOctoberOnTheLedgerWithSeptembersFees();
    fundLimit(TUK75, LocalDate.of(2026, 1, 1), "131000.00", "77000.00");
    service.reviewAllFunds(SEPTEMBER, FOURTH_BUSINESS_DAY_OF_OCTOBER);
    service.reviewAllFunds(OCTOBER, FOURTH_BUSINESS_DAY_OF_NOVEMBER);

    octobersFees();
    service.reviewTheFundsStillWithoutAReview(OCTOBER, FIFTH_BUSINESS_DAY_OF_NOVEMBER);

    var october = reviewRepository.findByFundAndMonth(TUK75, OCTOBER).orElseThrow();
    assertThat(october.reviewedOn()).isEqualTo(FIFTH_BUSINESS_DAY_OF_NOVEMBER);
    assertThat(List.of(october.softDrift(), october.hardDrift()))
        .usingRecursiveFieldByFieldElementComparator(AMOUNTS_BY_VALUE)
        .containsExactly(
            new Drift(amount("-99400.00"), amount("50000"), true, 2, 2),
            new Drift(amount("-45400.00"), amount("50000"), false, 0, 2));
  }

  @Test
  void anUnrecognisedPayoutIsAnErrorWhenItsMonthIsReviewedAndNotAgainOnceALaterMonthIs() {
    julyThroughOctoberOnTheLedgerWithTheirFees();
    dailyAccruals(TUK75, MANAGEMENT, AUGUST, "100.00");
    dailyAccruals(TUK75, DEPOT, AUGUST, "10.00");
    fundLimit(TUK75, LocalDate.of(2026, 1, 1), "131000.00", "77000.00");

    service.reviewAllFunds(AUGUST, FOURTH_BUSINESS_DAY_OF_OCTOBER);
    service.reviewAllFunds(OCTOBER, FOURTH_BUSINESS_DAY_OF_NOVEMBER);

    var notifications = inOrder(notificationService);
    notifications
        .verify(notificationService)
        .sendMessage(
            header(AUGUST)
                + "\nPAYOUT REASON NOT RECOGNISED — left out of the buffer until it is mapped in"
                + " RegistrarPayoutReason"
                + "\n  TUK75: 1 registrar payout(s), 5000.00 EUR booked in 2026-08"
                + otherFundsWithoutAReserve(FOURTH_BUSINESS_DAY_OF_OCTOBER),
            INVESTMENT,
            ERROR);
    notifications
        .verify(notificationService)
        .sendMessage(
            header(OCTOBER) + otherFundsWithoutAReserve(FOURTH_BUSINESS_DAY_OF_NOVEMBER),
            INVESTMENT,
            ERROR);
  }

  @Test
  void anOutgoingBankEntryStillInSuspenseIsAnErrorUntilItIsReclassifiedAndTheMonthReviewedAgain() {
    julyThroughOctoberOnTheLedgerWithTheirFees();
    fundLimit(TUK75, LocalDate.of(2026, 1, 1), "131000.00", "77000.00");
    var bookingDate = LocalDate.of(2026, 8, 25);
    var externalReference = outgoingEntryInSuspense("7000.00", bookingDate, RECURRING);

    service.reviewAllFunds(SEPTEMBER, FOURTH_BUSINESS_DAY_OF_OCTOBER);
    fundBankLedger.reclassifySuspenseEntry(
        TUK75, new BigDecimal("-7000.00"), externalReference, REGISTRAR_PAYOUT, bookingDate);
    service.reviewAllFunds(SEPTEMBER, FOURTH_BUSINESS_DAY_OF_OCTOBER);

    var notifications = inOrder(notificationService);
    notifications
        .verify(notificationService)
        .sendMessage(
            header(SEPTEMBER)
                + "\nBANK DEBITS STILL IN SUSPENSE — a registrar payout among them is left out of"
                + " the outflows, so both recommended limits may come out low until it is"
                + " reclassified"
                + "\n  TUK75: 1 outgoing bank entry(ies) in suspense; window 2026-07..2026-09 (3"
                + " months)"
                + otherFundsWithoutAReserve(FOURTH_BUSINESS_DAY_OF_OCTOBER),
            INVESTMENT,
            ERROR);
    notifications
        .verify(notificationService)
        .sendMessage(
            header(SEPTEMBER) + otherFundsWithoutAReserve(FOURTH_BUSINESS_DAY_OF_OCTOBER),
            INVESTMENT,
            ERROR);
  }

  private static String header(YearMonth reviewMonth) {
    return ("CASH BUFFER REVIEW %s — the recommended day-to-day operating buffer (recurring and"
            + " one-off payouts; PEVA, RAVA and PIK cycle outflows left out), not the fund's total"
            + " cash requirement. The job recommends only; investment_fund_limit is never changed"
            + " by it.")
        .formatted(reviewMonth);
  }

  private static String otherFundsWithoutAReserve(LocalDate asOf) {
    return ("\nREVIEW COULD NOT RUN"
            + "\n  TUK00: no reserve_soft in investment_fund_limit (asOf=%s)"
            + "\n  TUV100: no reserve_soft in investment_fund_limit (asOf=%s)")
        .formatted(asOf, asOf);
  }

  private void julyThroughOctoberOnTheLedgerWithTheirFees() {
    julyThroughOctoberOnTheLedgerWithSeptembersFees();
    octobersFees();
  }

  private void julyThroughOctoberOnTheLedgerWithSeptembersFees() {
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
    dailyAccruals(TUK75, MANAGEMENT, SEPTEMBER, "100.00");
    dailyAccruals(TUK75, DEPOT, SEPTEMBER, "10.00");
  }

  private void octobersFees() {
    dailyAccruals(TUK75, MANAGEMENT, OCTOBER, "100.00");
    dailyAccruals(TUK75, DEPOT, OCTOBER, "10.00");
  }

  private void contribution(String amount, LocalDate bookingDate) {
    fundBankLedger.recordRegistrarContribution(
        TUK75, new BigDecimal(amount), randomUUID(), bookingDate, SYNTHETIC_CONTRIBUTION);
  }

  private void payout(String amount, LocalDate bookingDate, String remittance) {
    fundBankLedger.recordRegistrarPayout(
        TUK75,
        new BigDecimal(amount).negate(),
        randomUUID(),
        bookingDate,
        SYNTHETIC_PERSONAL_CODE + ", " + remittance);
  }

  private UUID outgoingEntryInSuspense(String amount, LocalDate bookingDate, String remittance) {
    var externalReference = randomUUID();
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal(amount).negate(),
        externalReference,
        FUND_INVESTMENT_CASH_CLEARING,
        bookingDate,
        new UnclassifiedEntryDetails(
            null, null, SYNTHETIC_PERSONAL_CODE + ", " + remittance, null));
    return externalReference;
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

  private static MonthlyFlows flows(
      YearMonth month,
      String inflow,
      String recurring,
      String tail,
      String cycle,
      String unrecognised,
      int unrecognisedPayouts) {
    return new MonthlyFlows(
        month,
        amount(inflow),
        amount(recurring),
        amount(tail),
        amount(cycle),
        amount(unrecognised),
        unrecognisedPayouts);
  }

  private static BigDecimal amount(String value) {
    return new BigDecimal(value);
  }
}
