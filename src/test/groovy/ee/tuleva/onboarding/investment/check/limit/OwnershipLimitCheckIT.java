package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.HARD;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.SOFT;
import static ee.tuleva.onboarding.investment.check.limit.CheckType.OWNERSHIP;
import static ee.tuleva.onboarding.investment.check.limit.UnderlyingFunds.EODHD_EUR_USD_STORAGE_KEY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.check.limit.EODHDFundSizeClient.FundSize;
import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.NotChecked;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  OwnershipLimitCheckService.class,
  OwnershipLimitChecker.class,
  OwnershipLimitProvider.class,
  HeldSecurities.class,
  UnderlyingFunds.class,
  NavReportPositionProvider.class,
  LimitCheckEventWriter.class,
  OwnershipLimitCheckIT.FixedClock.class
})
class OwnershipLimitCheckIT {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final LocalDate MONTH_END = LocalDate.of(2026, 9, 30);
  private static final String INVESCO_EM = "IE00BMDBMY19";
  private static final String INVESCO_EM_TICKER = "ESGM.XETRA";
  private static final String XTRACKERS_CANADA = "LU0476289540";
  private static final BigDecimal HUNDRED_MILLION = new BigDecimal("100000000");
  private static final LocalDate EODHD_UPDATED = LocalDate.of(2026, 10, 3);

  @Autowired private OwnershipLimitCheckService service;
  @Autowired private LimitCheckEventRepository limitCheckEventRepository;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private EODHDFundSizeClient fundSizeClient;
  @MockitoBean private InstrumentReferenceService instrumentReferenceService;
  @MockitoBean private FundValueProvider fundValueProvider;

  @TestConfiguration
  static class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-06T04:45:00Z"), TALLINN);
    }
  }

  @Test
  void aTkf100HoldingOverTwentyPercentOfItsUnderlyingFund_isSoftAgainstTheSeededLimit() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "21000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.checkDate()).isEqualTo(MONTH_END);
              assertThat(result.holdings())
                  .singleElement()
                  .usingRecursiveComparison()
                  .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                  .isEqualTo(
                      new OwnershipBreach(
                          INVESCO_EM,
                          "Invesco MSCI EM Universal Screened",
                          new BigDecimal("21000000"),
                          HUNDRED_MILLION,
                          HUNDRED_MILLION,
                          "EUR",
                          EODHD_UPDATED,
                          new BigDecimal("21"),
                          new BigDecimal("20"),
                          new BigDecimal("25"),
                          SOFT));
            });
    assertThat(storedOwnershipEvent().isBreachesFound()).isTrue();
  }

  @Test
  void theLimitInForceAtMonthEnd_isUsed_notAnOlderOrALaterOne() {
    insertOwnershipLimit(LocalDate.of(2026, 9, 15), "30", "40");
    insertOwnershipLimit(LocalDate.of(2026, 10, 15), "10", "15");
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "12000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().holdings())
        .singleElement()
        .satisfies(
            holding -> {
              assertThat(holding.softLimitPercent()).isEqualByComparingTo("20");
              assertThat(holding.hardLimitPercent()).isEqualByComparingTo("25");
              assertThat(holding.severity()).isEqualTo(OK);
            });
  }

  @Test
  void aHoldingOfTwentyFivePercentOfItsUnderlyingFund_isHard() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "25000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.worstSeverity()).isEqualTo(HARD);
  }

  @Test
  void aHoldingAtTwentyPercent_isOk_andTheStoredEventCarriesNoBreach() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "20000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.worstSeverity()).isEqualTo(OK);
    assertThat(run.coveredEveryHolding()).isTrue();
    assertThat(storedOwnershipEvent().isBreachesFound()).isFalse();
  }

  @Test
  void theNavReportsMarketValue_isUsedOverTheCustodiansWhenTheReportHasTheIsin() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "1000000");
    insertPublishedNavReportSecurity(MONTH_END, INVESCO_EM, "22000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.worstSeverity()).isEqualTo(SOFT);
  }

  @Test
  void rowsOfOneIsinUnderTwoNames_areSummedIntoOneHolding() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "INVESCO EM ACC", "15000000");
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "INVESCO EM ACC II", "10000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().holdings())
        .singleElement()
        .satisfies(holding -> assertThat(holding.severity()).isEqualTo(HARD));
  }

  @Test
  void aNavReportValueForAnIsinSplitAcrossTwoCustodianRows_isCountedOnce() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "INVESCO EM ACC", "8000000");
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "INVESCO EM ACC II", "8000000");
    insertPublishedNavReportSecurity(MONTH_END, INVESCO_EM, "16000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().holdings())
        .singleElement()
        .satisfies(
            holding -> {
              assertThat(holding.holdingValue()).isEqualByComparingTo("16000000");
              assertThat(holding.severity()).isEqualTo(OK);
            });
  }

  @Test
  void eachUnderlyingFund_isCheckedAgainstItsOwnSize() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    given(instrumentReferenceService.findByIsin(XTRACKERS_CANADA))
        .willReturn(
            Optional.of(
                instrument(XTRACKERS_CANADA)
                    .displayName("Xtrackers MSCI Canada Screened")
                    .eodhdTicker("D5BH.XETRA")
                    .build()));
    given(fundSizeClient.fetch("D5BH.XETRA"))
        .willReturn(new FundSize.Reported(new BigDecimal("1000000000"), "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "21000000");
    insertSecurity(TKF100.name(), MONTH_END, XTRACKERS_CANADA, "21000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().holdings())
        .extracting(OwnershipBreach::isin, OwnershipBreach::severity)
        .containsExactlyInAnyOrder(tuple(INVESCO_EM, SOFT), tuple(XTRACKERS_CANADA, OK));
  }

  @Test
  void aMonthWithOnlyThePreviousMonthsPositions_isNotChecked_insteadOfCheckedOnStaleData() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), LocalDate.of(2026, 8, 31), INVESCO_EM, "1000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results()).isEmpty();
    assertThat(run.fundsNotChecked())
        .containsExactly(
            new NotChecked(TKF100, "no positions in 2026-09, the latest are from 2026-08-31"));
    assertThat(service.everyFundIsChecked(SEPTEMBER)).isFalse();
  }

  @Test
  void aFundSizeEodhdLastUpdatedBeforeTheMonthBegan_isUnverified() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", LocalDate.of(2026, 8, 15)));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "21000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().unverified())
        .singleElement()
        .satisfies(
            holding ->
                assertThat(holding.reason())
                    .isEqualTo(
                        "EODHD last updated the fund size on 2026-08-15, before 2026-09 began"));
  }

  @Test
  void aHoldingWhoseSizingThrows_isUnverified_andTheOtherHoldingsAreStillChecked() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    given(instrumentReferenceService.findByIsin(XTRACKERS_CANADA))
        .willReturn(
            Optional.of(
                instrument(XTRACKERS_CANADA)
                    .displayName("Xtrackers MSCI Canada Screened")
                    .eodhdTicker("D5BH.XETRA")
                    .build()));
    given(fundSizeClient.fetch("D5BH.XETRA")).willThrow(new IllegalStateException("boom"));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "21000000");
    insertSecurity(TKF100.name(), MONTH_END, XTRACKERS_CANADA, "1000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().holdings())
        .extracting(OwnershipBreach::isin, OwnershipBreach::severity)
        .containsExactly(tuple(INVESCO_EM, SOFT));
    assertThat(run.results().getFirst().unverified())
        .extracting(UnverifiedHolding::isin, UnverifiedHolding::reason)
        .containsExactly(tuple(XTRACKERS_CANADA, "assessment failed: IllegalStateException: boom"));
  }

  @Test
  void aHeldSecurityTheNavReportValuesAtZero_isStillListed_notDroppedAsSoldOut() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "1000000");
    insertPublishedNavReportSecurity(MONTH_END, INVESCO_EM, "0");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().holdings())
        .extracting(OwnershipBreach::isin)
        .containsExactly(INVESCO_EM);
  }

  @Test
  void aMonthCountsAsChecked_onlyOnceItsOwnershipEventIsStored() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "1000000");

    var before = service.everyFundIsChecked(SEPTEMBER);
    service.checkMonthEnd(SEPTEMBER);

    assertThat(before).isFalse();
    assertThat(service.everyFundIsChecked(SEPTEMBER)).isTrue();
  }

  @Test
  void aMonthWhoseRunLeftAHoldingUnverified_isRetried_andCountsAsCheckedOnceEveryHoldingIsSized() {
    givenInvescoEmInstrument();
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "1000000");
    givenFundSize(new FundSize.Unavailable("EODHD answered HTTP 503"));
    service.checkMonthEnd(SEPTEMBER);
    var afterTheIncompleteRun = service.everyFundIsChecked(SEPTEMBER);

    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    service.checkMonthEnd(SEPTEMBER);

    assertThat(afterTheIncompleteRun).isFalse();
    assertThat(service.everyFundIsChecked(SEPTEMBER)).isTrue();
  }

  @Test
  void aFundSizeWithoutAnEodhdUpdateDate_isUnverified_ratherThanTakenAsCurrent() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", null));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "21000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().unverified())
        .extracting(UnverifiedHolding::reason)
        .containsExactly("EODHD gives no update date for the fund size");
  }

  @Test
  void aMonthNeverCountsAsChecked_whenNoFundHasAnOwnershipLimit() {
    jdbcClient.sql("DELETE FROM investment_ownership_limit").update();

    assertThat(service.everyFundIsChecked(SEPTEMBER)).isFalse();
  }

  @Test
  void theMonthsLastPositionDate_isChecked_notALaterOne() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END.minusDays(1), INVESCO_EM, "1000000");
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "1000000");
    insertSecurity(TKF100.name(), MONTH_END.plusDays(1), INVESCO_EM, "30000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.checkDate()).isEqualTo(MONTH_END);
              assertThat(result.worstSeverity()).isEqualTo(OK);
            });
  }

  @Test
  void aFundSizeReportedInUsd_isConvertedAtTheEurUsdRateBeforeComparing() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(new BigDecimal("116000000"), "USD", EODHD_UPDATED));
    given(fundValueProvider.getLatestValue(EODHD_EUR_USD_STORAGE_KEY, LocalDate.of(2026, 10, 6)))
        .willReturn(
            Optional.of(
                new FundValue(
                    EODHD_EUR_USD_STORAGE_KEY,
                    MONTH_END,
                    new BigDecimal("1.16"),
                    "EODHD",
                    Instant.parse("2026-10-01T05:00:00Z"))));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "21000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().holdings())
        .singleElement()
        .satisfies(
            holding -> {
              assertThat(holding.underlyingFundSize()).isEqualByComparingTo(HUNDRED_MILLION);
              assertThat(holding.severity()).isEqualTo(SOFT);
            });
  }

  @Test
  void aHoldingWithoutAnEodhdFundSize_isUnverified_soTheMonthIsNotCoveredInFull() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Unavailable("EODHD has no total assets"));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "21000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().unverified())
        .containsExactly(
            new UnverifiedHolding(
                INVESCO_EM,
                "Invesco MSCI EM Universal Screened",
                new BigDecimal("21000000.00"),
                "EODHD has no total assets"));
    assertThat(run.coveredEveryHolding()).isFalse();
  }

  @Test
  void aFundSizeInUsdWithoutAStoredEurUsdRate_isUnverified() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(new BigDecimal("116000000"), "USD", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "21000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().unverified())
        .singleElement()
        .satisfies(
            holding ->
                assertThat(holding.reason()).isEqualTo("no EUR rate for a fund size in USD"));
  }

  @Test
  void aFundSizeInACurrencyWithoutAnEurRate_isUnverified() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "GBP", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "21000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().unverified())
        .singleElement()
        .satisfies(
            holding ->
                assertThat(holding.reason()).isEqualTo("no EUR rate for a fund size in GBP"));
  }

  @Test
  void aSecurityRowWithoutAMarketValue_isUnverified_notDroppedAsZero() {
    givenInvescoEmInstrument();
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, INVESCO_EM, null);

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().unverified())
        .containsExactly(
            new UnverifiedHolding(
                INVESCO_EM, "Invesco MSCI EM Universal Screened", null, "no market value"));
  }

  @Test
  void aSecurityRowWithoutAnIsin_isUnverified_notSkipped() {
    insertSecurityWithoutIsin(TKF100.name(), MONTH_END, "UNKNOWN FUND ACC", "21000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().unverified())
        .containsExactly(
            new UnverifiedHolding(
                null, "UNKNOWN FUND ACC", new BigDecimal("21000000.00"), "no ISIN"));
  }

  @Test
  void aSoldOutRowWithNoValue_isNeitherCheckedNorReportedUnverified() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "1000000");
    insertSecurity(TKF100.name(), MONTH_END, XTRACKERS_CANADA, "0");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().holdings())
        .extracting(OwnershipBreach::isin)
        .containsExactly(INVESCO_EM);
    assertThat(run.results().getFirst().unverified()).isEmpty();
  }

  @Test
  void aHoldingWithoutAnEodhdTicker_isUnverified() {
    given(instrumentReferenceService.findByIsin(INVESCO_EM))
        .willReturn(Optional.of(instrument(INVESCO_EM).build()));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "21000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results().getFirst().unverified())
        .singleElement()
        .satisfies(
            holding -> assertThat(holding.reason()).isEqualTo("no EODHD ticker in instruments"));
  }

  @Test
  void pensionFundsHaveNoOwnershipLimit_soOnlyTkf100IsChecked_andItsMissingPositionsAreNamed() {
    insertSecurity("TUK75", MONTH_END, INVESCO_EM, "900000000");

    var run = service.checkMonthEnd(SEPTEMBER);

    assertThat(run.results()).isEmpty();
    assertThat(run.fundsNotChecked())
        .containsExactly(new NotChecked(TKF100, "no positions in 2026-09 or before"));
  }

  @Test
  void theOwnershipEvent_doesNotCountAsADayTheDailyLimitCheckRan() {
    givenInvescoEmInstrument();
    givenFundSize(new FundSize.Reported(HUNDRED_MILLION, "EUR", EODHD_UPDATED));
    insertSecurity(TKF100.name(), MONTH_END, INVESCO_EM, "1000000");

    service.checkMonthEnd(SEPTEMBER);

    assertThat(storedOwnershipEvent()).isNotNull();
    assertThat(limitCheckEventRepository.findDistinctCheckDates(TKF100, MONTH_END, MONTH_END))
        .isEmpty();
  }

  private void givenInvescoEmInstrument() {
    given(instrumentReferenceService.findByIsin(INVESCO_EM))
        .willReturn(
            Optional.of(
                instrument(INVESCO_EM)
                    .displayName("Invesco MSCI EM Universal Screened")
                    .eodhdTicker(INVESCO_EM_TICKER)
                    .build()));
  }

  private void givenFundSize(FundSize fundSize) {
    given(fundSizeClient.fetch(INVESCO_EM_TICKER)).willReturn(fundSize);
  }

  private LimitCheckEvent storedOwnershipEvent() {
    return limitCheckEventRepository.findByFundAndCheckDate(TKF100, MONTH_END).stream()
        .filter(event -> event.getCheckType() == OWNERSHIP)
        .findFirst()
        .orElseThrow();
  }

  private void insertSecurity(String fund, LocalDate navDate, String isin, String marketValue) {
    insertSecurity(fund, navDate, isin, isin, marketValue);
  }

  private void insertSecurityWithoutIsin(
      String fund, LocalDate navDate, String accountName, String marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_position
            (nav_date, fund_code, account_type, account_name, market_value)
            VALUES (:navDate, :fund, 'SECURITY', :accountName, :marketValue)
            """)
        .param("navDate", navDate)
        .param("fund", fund)
        .param("accountName", accountName)
        .param("marketValue", new BigDecimal(marketValue))
        .update();
  }

  private void insertSecurity(
      String fund,
      LocalDate navDate,
      String isin,
      String accountName,
      @Nullable String marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_position
            (nav_date, fund_code, account_type, account_name, account_id, market_value)
            VALUES (:navDate, :fund, 'SECURITY', :accountName, :isin, :marketValue)
            """)
        .param("navDate", navDate)
        .param("fund", fund)
        .param("accountName", accountName)
        .param("isin", isin)
        .param("marketValue", marketValue == null ? null : new BigDecimal(marketValue))
        .update();
  }

  private void insertOwnershipLimit(LocalDate effectiveDate, String soft, String hard) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ownership_limit
              (effective_date, fund_code, soft_limit_percent, hard_limit_percent)
            VALUES (:effectiveDate, 'TKF100', :soft, :hard)
            """)
        .param("effectiveDate", effectiveDate)
        .param("soft", new BigDecimal(soft))
        .param("hard", new BigDecimal(hard))
        .update();
  }

  private void insertPublishedNavReportSecurity(
      LocalDate navDate, String isin, String marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
              (nav_date, fund_code, account_type, account_name, account_id, market_value,
               currency, calculation_id, published_at)
            VALUES (:navDate, 'TKF100', 'SECURITY', :isin, :isin, :marketValue,
               'EUR', :calculationId, now())
            """)
        .param("navDate", navDate)
        .param("isin", isin)
        .param("marketValue", new BigDecimal(marketValue))
        .param("calculationId", UUID.randomUUID())
        .update();
  }
}
