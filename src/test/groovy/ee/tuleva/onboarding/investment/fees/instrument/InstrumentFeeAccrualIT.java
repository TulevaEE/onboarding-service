package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.fees.rate.RateStatus.PROVISIONAL;
import static ee.tuleva.onboarding.investment.fees.rate.RateStatus.RESOLVED;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.time.ZoneOffset.UTC;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.instrument.InstrumentReferenceRepository;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.fees.rate.AccrualRate;
import ee.tuleva.onboarding.investment.fees.rate.AccrualRates;
import ee.tuleva.onboarding.investment.fees.rate.RateStatus;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import ee.tuleva.onboarding.investment.transaction.OpenTradeSource;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  InstrumentFeeAccrualIT.FixedClock.class,
  InstrumentFeeAccrualService.class,
  ProviderAgreementRepository.class,
  InstrumentFeeAccrualRepository.class,
  HoldingReader.class,
  DayPriceReader.class,
  FundNavQueryService.class
})
class InstrumentFeeAccrualIT {

  private static final String ISIN = "ZZ0000000001";
  private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 20);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 21);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 22);
  private static final BigDecimal REBATE_RATE = new BigDecimal("0.00050000");
  private static final BigDecimal INVOICED_FEE_RATE = new BigDecimal("0.00010000");

  @TestConfiguration
  static class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-11-23T18:00:00Z"), UTC);
    }
  }

  @MockitoBean private AccrualRates accrualRates;
  @MockitoBean private OpenTradeSource openTradeSource;
  @MockitoBean private PriorityPriceProvider priorityPriceProvider;
  @MockitoBean private InstrumentReferenceService instrumentReferenceService;

  @Autowired private InstrumentFeeAccrualService service;
  @Autowired private InstrumentReferenceRepository instrumentReferenceRepository;
  @Autowired private NavReportRepository navReportRepository;
  @Autowired private FundPositionRepository fundPositionRepository;
  @Autowired private JdbcClient jdbcClient;

  private long instrumentFeeId;
  private long providerAgreementId;
  private long resolvedRateId;

  @BeforeEach
  void aSyntheticInstrumentWithAnAgreementAndNoUnlinkedOnes() {
    inTheInstrumentReference();
    providerAgreementId = aProviderAgreement();
    instrumentFeeId = anInstrumentFeeAgreement(providerAgreementId);
    resolvedRateId = aResolvedRate(instrumentFeeId);
    given(accrualRates.isinsWithoutAProviderAgreementOn(any())).willReturn(new TreeSet<>());
  }

  @Test
  void theDailyHoldingAccruesEveryCalendarDayOnTheLastNavsUnitsAndTheDaysOwnPrice() {
    agreedFrom(LocalDate.of(2026, 1, 1), "DAILY_HOLDING");
    givenTheRate(RESOLVED, resolvedRateId);
    navOn(FRIDAY, "1000.000", "12.00");
    ownPrice("12.34");

    var run = service.accrueSince(FRIDAY);

    assertThat(run.accruedDays()).containsExactly(FRIDAY, SATURDAY, SUNDAY);
    assertThat(run.failedDays()).isEmpty();
    assertThat(stored())
        .containsExactly(
            new StoredAccrual(
                "TUK75",
                FRIDAY,
                "DAILY_HOLDING",
                "RESOLVED",
                resolvedRateId,
                "1000.00000000",
                "NAV_REPORT",
                FRIDAY,
                "12.34000000",
                "BLACKROCK",
                "0.0169041096",
                "0.0033808219"),
            new StoredAccrual(
                "TUK75",
                SATURDAY,
                "DAILY_HOLDING",
                "RESOLVED",
                resolvedRateId,
                "1000.00000000",
                "NAV_REPORT",
                FRIDAY,
                "12.34000000",
                "BLACKROCK",
                "0.0169041096",
                "0.0033808219"),
            new StoredAccrual(
                "TUK75",
                SUNDAY,
                "DAILY_HOLDING",
                "RESOLVED",
                resolvedRateId,
                "1000.00000000",
                "NAV_REPORT",
                FRIDAY,
                "12.34000000",
                "BLACKROCK",
                "0.0169041096",
                "0.0033808219"));
  }

  @Test
  void theMonthEndHoldingAccruesOnSettledUnitsLeavingOutAnUnsettledBuy() {
    agreedFrom(LocalDate.of(2026, 1, 1), "MONTH_END_HOLDING");
    givenTheRate(PROVISIONAL, null);
    navOn(FRIDAY, "1000.000", "12.00");
    sebPositionOn(FRIDAY, "1000.000");
    given(openTradeSource.openTrades(FRIDAY))
        .willReturn(
            Optional.of(
                new OpenTrades(
                    List.of(
                        new OpenTrade(
                            TUK75,
                            BUY,
                            new BigDecimal("1440.00"),
                            null,
                            ISIN,
                            FRIDAY.plusDays(3),
                            new BigDecimal("120.000"),
                            null)),
                    Set.of(),
                    List.of())));
    ownPrice("12.34");

    service.accrueSince(SUNDAY);

    assertThat(stored())
        .containsExactly(
            new StoredAccrual(
                "TUK75",
                SUNDAY,
                "MONTH_END_HOLDING",
                "PROVISIONAL",
                null,
                "880.00000000",
                "SETTLED",
                FRIDAY,
                "12.34000000",
                "BLACKROCK",
                "0.0150822222",
                "0.0030164444"));
  }

  @Test
  void withoutAFeedPriceTheNavReportsPriceIsUsed() {
    agreedFrom(LocalDate.of(2026, 1, 1), "DAILY_HOLDING");
    givenTheRate(RESOLVED, resolvedRateId);
    navOn(FRIDAY, "1000.000", "12.00");

    service.accrueSince(SUNDAY);

    assertThat(stored())
        .singleElement()
        .satisfies(
            accrual -> {
              assertThat(accrual.price()).isEqualTo("12.00000000");
              assertThat(accrual.priceSource()).isEqualTo("NAV_REPORT");
            });
  }

  @Test
  void anExchangeTradedFundTakesThePriceTheNavUsedRatherThanTheFeedsNewest() {
    jdbcClient
        .sql("UPDATE instrument_reference SET eodhd_ticker = 'ZZ01.XETRA' WHERE isin = :isin")
        .param("isin", ISIN)
        .update();
    given(instrumentReferenceService.findByIsin(ISIN))
        .willReturn(instrumentReferenceRepository.findByIsin(ISIN));
    agreedFrom(LocalDate.of(2026, 1, 1), "DAILY_HOLDING");
    givenTheRate(RESOLVED, resolvedRateId);
    navOn(FRIDAY, "1000.000", "12.00");
    ownPrice("12.34");

    service.accrueSince(SUNDAY);

    assertThat(stored())
        .singleElement()
        .satisfies(
            accrual -> {
              assertThat(accrual.price()).isEqualTo("12.00000000");
              assertThat(accrual.priceSource()).isEqualTo("NAV_REPORT");
            });
  }

  @Test
  void whileThePreviousMonthHoldsProvisionalAccrualsTheRecentRunRecalculatesItFromItsFirstDay() {
    var versionId = agreedFrom(LocalDate.of(2026, 1, 1), "DAILY_HOLDING");
    aProvisionalAccrualOn(LocalDate.of(2026, 10, 5), versionId);

    var run = service.accrueTheRecentDays();

    assertThat(run.accruedDays().getFirst()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(run.accruedDays().getLast()).isEqualTo(SUNDAY);
  }

  @Test
  void aSecondRunReplacesTheDaysAccrualsInsteadOfAddingToThem() {
    agreedFrom(LocalDate.of(2026, 1, 1), "DAILY_HOLDING");
    givenTheRate(RESOLVED, resolvedRateId);
    navOn(FRIDAY, "1000.000", "12.00");
    ownPrice("12.34");

    service.accrueSince(FRIDAY);
    service.accrueSince(FRIDAY);

    assertThat(stored()).hasSize(3);
  }

  @Test
  void anAgreementWhoseProviderHasNoVersionValidOnTheDayIsNotAccrued() {
    agreedFrom(LocalDate.of(2026, 12, 1), "DAILY_HOLDING");
    givenTheRate(RESOLVED, resolvedRateId);
    navOn(FRIDAY, "1000.000", "12.00");
    ownPrice("12.34");

    var run = service.accrueSince(SUNDAY);

    assertThat(run.accruedDays()).containsExactly(SUNDAY);
    assertThat(stored()).isEmpty();
  }

  @Test
  void aDayThatFailsKeepsItsStoredAccrualsAndTheOtherDaysStillRun() {
    agreedFrom(LocalDate.of(2026, 1, 1), "DAILY_HOLDING");
    givenTheRate(RESOLVED, resolvedRateId);
    navOn(FRIDAY, "1000.000", "12.00");
    ownPrice("12.34");
    service.accrueSince(FRIDAY);
    given(accrualRates.on(SATURDAY))
        .willThrow(new DataAccessResourceFailureException("synthetic outage"));

    var run = service.accrueSince(FRIDAY);

    assertThat(run.accruedDays()).containsExactly(FRIDAY, SUNDAY);
    assertThat(run.failedDays()).containsExactly(SATURDAY);
    assertThat(stored())
        .extracting(StoredAccrual::accrualDate)
        .containsExactly(FRIDAY, SATURDAY, SUNDAY);
  }

  @Test
  void theRunNamesTheAgreementsWithoutAProviderAgreement() {
    given(accrualRates.isinsWithoutAProviderAgreementOn(SUNDAY))
        .willReturn(new TreeSet<>(Set.of("ZZ0000000009")));

    var run = service.accrueSince(SUNDAY);

    assertThat(run.isinsWithoutAProviderAgreement()).containsExactly("ZZ0000000009");
    assertThat(stored()).isEmpty();
  }

  @Test
  void theRecentDaysAreTheWeekEndingYesterdayInTallinn() {
    var run = service.accrueTheRecentDays();

    assertThat(run.accruedDays())
        .containsExactlyElementsOf(
            LocalDate.of(2026, 11, 16).datesUntil(LocalDate.of(2026, 11, 23)).toList());
  }

  private void givenTheRate(RateStatus status, @Nullable Long rateId) {
    given(accrualRates.on(any()))
        .willReturn(
            List.of(
                new AccrualRate(
                    instrumentFeeId,
                    ISIN,
                    providerAgreementId,
                    status,
                    rateId,
                    REBATE_RATE,
                    INVOICED_FEE_RATE)));
  }

  private void ownPrice(String price) {
    given(priorityPriceProvider.resolve(any(), any()))
        .willReturn(
            Optional.of(
                new FundValue(
                    ISIN + ".BLACKROCK",
                    FRIDAY,
                    new BigDecimal(price),
                    "BLACKROCK",
                    Instant.parse("2026-11-21T08:00:00Z"))));
  }

  private void navOn(LocalDate navDate, String units, String price) {
    navReportRepository.save(
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode(TUK75.getCode())
            .accountType("SECURITY")
            .accountName("Synthetic holding")
            .accountId(ISIN)
            .quantity(new BigDecimal(units))
            .marketPrice(new BigDecimal(price))
            .marketValue(new BigDecimal(units).multiply(new BigDecimal(price)))
            .calculationId(UUID.randomUUID())
            .publishedAt(navDate.atTime(16, 0).toInstant(UTC))
            .build());
  }

  private void sebPositionOn(LocalDate positionDate, String units) {
    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(positionDate)
            .fund(TUK75)
            .accountType(SECURITY)
            .accountName("Synthetic holding")
            .accountId(ISIN)
            .quantity(new BigDecimal(units))
            .build());
  }

  private void inTheInstrumentReference() {
    jdbcClient
        .sql(
            """
            INSERT INTO instrument_reference (isin, display_name, instrument_type, asset_class)
            VALUES (:isin, :isin, 'FUND', 'equity')
            """)
        .param("isin", ISIN)
        .update();
  }

  private long aProviderAgreement() {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql("INSERT INTO investment_provider_agreement (created_at) VALUES (CURRENT_TIMESTAMP)")
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey()).longValue();
  }

  private long agreedFrom(LocalDate validFrom, String method) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_provider_agreement_version
              (provider_agreement_id, accrual_method, payment_period, payer_label, valid_from)
            VALUES (:providerAgreementId, :method, 'MONTH', 'Synthetic payer', :validFrom)
            """)
        .param("providerAgreementId", providerAgreementId)
        .param("method", method)
        .param("validFrom", validFrom)
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey()).longValue();
  }

  private void aProvisionalAccrualOn(LocalDate day, long providerAgreementVersionId) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee_accrual
              (fund_code, isin, accrual_date, instrument_fee_id, provider_agreement_version_id,
               accrual_method, rate_status, rebate_rate, invoiced_fee_rate, units, units_source,
               units_date, price, price_date, price_source, rebate_amount, invoiced_fee_amount,
               calculated_at)
            VALUES ('TUK75', :isin, :day, :instrumentFeeId, :versionId, 'DAILY_HOLDING',
                    'PROVISIONAL', 0.00050000, 0.00010000, 1000, 'NAV_REPORT', :day, 12.34, :day,
                    'BLACKROCK', 0.0169041096, 0.0033808219, CURRENT_TIMESTAMP)
            """)
        .param("isin", ISIN)
        .param("day", day)
        .param("instrumentFeeId", instrumentFeeId)
        .param("versionId", providerAgreementVersionId)
        .update();
  }

  private long anInstrumentFeeAgreement(long providerAgreementId) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee
              (isin, published_ocf, rebate_kind, rebate_terms, valid_from, provider_agreement_id)
            VALUES (:isin, 0.00070000, 'NONE', '{}', DATE '2026-01-01', :providerAgreementId)
            """)
        .param("isin", ISIN)
        .param("providerAgreementId", providerAgreementId)
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey()).longValue();
  }

  private long aResolvedRate(long instrumentFeeId) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee_rate
              (isin, period_start, period_end, published_ocf, rebate_rate, invoiced_fee_rate,
               net_ocf, rate_basis, rebate_kind, instrument_fee_id)
            VALUES (:isin, DATE '2026-11-01', DATE '2026-11-30', 0.00070000, 0.00050000,
                    0.00010000, 0.00030000, 'AGREEMENT', 'FIXED', :instrumentFeeId)
            """)
        .param("isin", ISIN)
        .param("instrumentFeeId", instrumentFeeId)
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey()).longValue();
  }

  private List<StoredAccrual> stored() {
    return jdbcClient
        .sql(
            """
            SELECT fund_code, accrual_date, accrual_method, rate_status, instrument_fee_rate_id,
                   units, units_source, units_date, price, price_source, rebate_amount,
                   invoiced_fee_amount
            FROM investment_instrument_fee_accrual
            ORDER BY accrual_date, fund_code
            """)
        .query(
            (rs, rowNum) ->
                new StoredAccrual(
                    rs.getString("fund_code"),
                    rs.getObject("accrual_date", LocalDate.class),
                    rs.getString("accrual_method"),
                    rs.getString("rate_status"),
                    rs.getObject("instrument_fee_rate_id", Long.class),
                    rs.getBigDecimal("units").toPlainString(),
                    rs.getString("units_source"),
                    rs.getObject("units_date", LocalDate.class),
                    rs.getBigDecimal("price").toPlainString(),
                    rs.getString("price_source"),
                    rs.getBigDecimal("rebate_amount").toPlainString(),
                    rs.getBigDecimal("invoiced_fee_amount").toPlainString()))
        .list();
  }

  private record StoredAccrual(
      String fundCode,
      LocalDate accrualDate,
      String method,
      String rateStatus,
      @Nullable Long instrumentFeeRateId,
      String units,
      String unitsSource,
      LocalDate unitsDate,
      String price,
      String priceSource,
      String rebateAmount,
      String invoicedFeeAmount) {}
}
