package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.TransactionCostSource.PUBLISHED_OCF;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationReadConfiguration;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

@DataJpaTest
@Import({
  JdbcFundValueRepository.class,
  IndexOnlyKidSeries.class,
  KidConfiguration.class,
  KidFiguresService.class,
  PerformanceScenarioCalculator.class,
  StressScenarioCalculator.class,
  ScenarioPresentation.class,
  CostsOverTimeCalculator.class,
  KidCalculationRepository.class,
  KidCalculationService.class,
  OcfPublicationReadConfiguration.class,
  KidCalculationServiceIT.ClockForTheTest.class
})
class KidCalculationServiceIT {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");
  private static final YearMonth JANUARY = YearMonth.of(2026, 1);
  private static final List<YearMonth> JANUARY_TO_SEPTEMBER =
      Stream.iterate(JANUARY, month -> !month.isAfter(SEPTEMBER), month -> month.plusMonths(1))
          .toList();

  @Autowired private KidCalculationService service;
  @Autowired private KidCalculationRepository repository;
  @Autowired private JdbcFundValueRepository fundValues;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private SettableClock clock;

  @BeforeEach
  void dayAfterTheSeptemberPeriodEnds() {
    clock.set(NOW);
  }

  @Test
  void theJobCalculatesEveryMonthFromTheFirstKidMonthOldestFirst() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    var kid = KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 1, 12), "0.00500000");

    service.calculateTheMissingMonths();

    assertThat(storedMonthsInTheOrderStored()).isEqualTo(JANUARY_TO_SEPTEMBER);
    assertThat(
            repository.findMonthsPublishedOrCompleteInTheirLatestVersion(
                TKF100, JANUARY, SEPTEMBER))
        .containsExactlyInAnyOrderElementsOf(JANUARY_TO_SEPTEMBER);
    assertThat(repository.findLatest(TKF100, SEPTEMBER))
        .contains(
            new StoredKidCalculation(
                calculationIdOf(SEPTEMBER),
                TKF100,
                SEPTEMBER,
                1,
                List.of(),
                "MSCI_ACWI",
                LocalDate.of(2026, 9, 30),
                new OcfBasis(kid, LocalDate.of(2026, 1, 12), new BigDecimal("0.00500000"), null),
                PUBLISHED_OCF,
                NOW));
  }

  @Test
  void theJobStoresTheMonthsScenarioAndCostRowsUnroundedAndPrinted() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 18), "0.00500000");

    service.calculateTheMissingMonths();

    assertThat(
            jdbcClient
                .sql(
                    """
                    SELECT s.unrounded_amount_eur, s.unrounded_average_return_per_year,
                           s.window_source, s.window_start, s.window_end, s.window_months
                    FROM investment_kid_scenario s
                    JOIN investment_kid_calculation c ON c.id = s.calculation_id
                    WHERE c.calculation_month = :month
                      AND s.horizon_years = 5 AND s.kind = 'UNFAVOURABLE'
                    """)
                .param("month", SEPTEMBER.atDay(1))
                .query()
                .singleRow())
        .containsValues(
            new BigDecimal("8618.55797059"),
            new BigDecimal("-0.02929577"),
            "SHORTER",
            java.sql.Date.valueOf("2025-09-30"),
            java.sql.Date.valueOf("2026-09-30"),
            12);
    assertThat(
            jdbcClient
                .sql(
                    """
                    SELECT k.printed_total_costs_eur, k.unrounded_total_costs_eur,
                           k.unrounded_annual_cost_impact, k.unrounded_return_per_year_after_costs
                    FROM investment_kid_cost k
                    JOIN investment_kid_calculation c ON c.id = k.calculation_id
                    WHERE c.calculation_month = :month AND k.horizon_years = 5
                    """)
                .param("month", SEPTEMBER.atDay(1))
                .query()
                .singleRow())
        .containsValues(
            new BigDecimal("273"),
            new BigDecimal("273.22744679"),
            new BigDecimal("0.00516621"),
            new BigDecimal("0.02807509"));
  }

  @Test
  void theJobRecordsTheCalculationAnOcfFigureCameFrom() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    var calculated = kidFigureCalculatedFrom(YearMonth.of(2026, 8), LocalDate.of(2026, 9, 18));

    service.calculateTheMissingMonths();

    assertThat(repository.findLatest(TKF100, SEPTEMBER).orElseThrow().ocf())
        .isEqualTo(
            new OcfBasis(
                calculated.publicationId(),
                LocalDate.of(2026, 9, 18),
                new BigDecimal("0.00400000"),
                new OcfBasis.CalculatedFrom(calculated.snapshotId(), YearMonth.of(2026, 8))));

    assertThat(storedOcfRateSourceOf(SEPTEMBER)).isEqualTo("CALCULATION_UNROUNDED");
  }

  @Test
  void theJobRecordsAGivenOcfFigureAsThePublishedFigure() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 18), "0.00500000");

    service.calculateTheMissingMonths();

    assertThat(storedOcfRateSourceOf(SEPTEMBER)).isEqualTo("PUBLISHED_FIGURE");
  }

  @Test
  void aSecondRunStoresNothingOnceEveryMonthIsCalculatedOrRefusedForTheSameReasons() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 18), "0.00500000");
    service.calculateTheMissingMonths();
    var afterTheFirstRun = storedVersions();

    service.calculateTheMissingMonths();

    assertThat(afterTheFirstRun).isEqualTo(9);
    assertThat(storedVersions()).isEqualTo(afterTheFirstRun);
  }

  @Test
  void theJobStoresAMonthWithNoOcfInForceAtItsEndAsARefusalAndCalculatesItOnceOneIs() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 10, 29), "0.00500000");

    service.calculateTheMissingMonths();

    var september = repository.findLatest(TKF100, SEPTEMBER).orElseThrow();
    assertThat(september.complete()).isFalse();
    assertThat(september.ocf()).isNull();
    assertThat(september.refusalReasons()).hasSize(1);
    assertThat(
            repository.findMonthsPublishedOrCompleteInTheirLatestVersion(
                TKF100, JANUARY, SEPTEMBER))
        .isEmpty();
    assertThat(storedRows("investment_kid_scenario")).isZero();

    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 1, 12), "0.00500000");
    service.calculateTheMissingMonths();

    assertThat(
            repository.findMonthsPublishedOrCompleteInTheirLatestVersion(
                TKF100, JANUARY, SEPTEMBER))
        .containsExactlyInAnyOrderElementsOf(JANUARY_TO_SEPTEMBER);
  }

  @Test
  void theJobWaitsWithoutStoringForAMonthEndCloseThatIsLateButNotYetStopped() {
    fundValues.saveAll(
        SyntheticIndexSeries.weekdayCloses("MSCI_ACWI").stream()
            .filter(close -> close.date().isBefore(LocalDate.of(2026, 9, 30)))
            .toList());
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 1, 12), "0.00500000");
    clock.set(Instant.parse("2026-10-02T09:00:00Z"));

    service.calculateTheMissingMonths();

    assertThat(storedMonthsInTheOrderStored())
        .isEqualTo(JANUARY_TO_SEPTEMBER.subList(0, JANUARY_TO_SEPTEMBER.size() - 1));
  }

  @Test
  void theJobStoresAStoppedSeriesAsTheRefusalOfEachMonthItCannotCloseOnlyOnce() {
    fundValues.saveAll(
        SyntheticIndexSeries.weekdayCloses("MSCI_ACWI").stream()
            .filter(close -> close.date().isBefore(LocalDate.of(2026, 10, 9)))
            .toList());
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 1, 12), "0.00500000");
    clock.set(Instant.parse("2026-11-03T09:00:00Z"));

    service.calculateTheMissingMonths();
    clock.set(Instant.parse("2026-11-04T09:00:00Z"));
    service.calculateTheMissingMonths();

    assertThat(
            repository.findMonthsPublishedOrCompleteInTheirLatestVersion(
                TKF100, JANUARY, YearMonth.of(2026, 10)))
        .containsExactlyInAnyOrderElementsOf(JANUARY_TO_SEPTEMBER);
    var october = repository.findLatest(TKF100, YearMonth.of(2026, 10)).orElseThrow();
    assertThat(october.version()).isEqualTo(1);
    assertThat(october.complete()).isFalse();
    assertThat(october.refusalReasons()).hasSize(1);
  }

  @Test
  void aBackfillAndTheJobRefuseAMonthOfAStoppedSeriesForOneReasonSoItIsStoredOnce() {
    fundValues.saveAll(
        SyntheticIndexSeries.weekdayCloses("MSCI_ACWI").stream()
            .filter(close -> close.date().isBefore(LocalDate.of(2026, 10, 9)))
            .toList());
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 1, 12), "0.00500000");
    clock.set(Instant.parse("2026-11-03T09:00:00Z"));

    service.calculateAndStore(TKF100, YearMonth.of(2026, 10));
    service.calculateTheMissingMonths();

    assertThat(versionsOf(YearMonth.of(2026, 10))).isEqualTo(1);
  }

  @Test
  void theJobRetriesAMonthWhoseLatestVersionIsARefusalEvenIfAnEarlierOneWasComplete() {
    var closes = SyntheticIndexSeries.weekdayCloses("MSCI_ACWI");
    fundValues.saveAll(closes);
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 18), "0.00500000");
    service.calculateTheMissingMonths();
    jdbcClient
        .sql(
            "DELETE FROM index_values WHERE key = 'MSCI_ACWI'"
                + " AND date BETWEEN DATE '2023-03-01' AND DATE '2023-03-20'")
        .update();
    var refused = service.calculateAndStore(TKF100, SEPTEMBER);
    fundValues.saveAll(closes.stream().filter(close -> inTheMarch2023Gap(close.date())).toList());

    service.calculateTheMissingMonths();

    assertThat(refused.complete()).isFalse();
    assertThat(versionsOf(SEPTEMBER)).isEqualTo(3);
    assertThat(repository.findLatest(TKF100, SEPTEMBER).orElseThrow().complete()).isTrue();
    assertThat(
            repository.findMonthsPublishedOrCompleteInTheirLatestVersion(
                TKF100, JANUARY, SEPTEMBER))
        .containsExactly(SEPTEMBER);
  }

  @Test
  void theJobStoresARefusedMonthOnceWithItsReasonAndNoNumbers() {
    fundValues.saveAll(
        SyntheticIndexSeries.weekdayCloses("MSCI_ACWI").stream()
            .filter(close -> !inTheMarch2023Gap(close.date()))
            .toList());
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 1, 12), "0.00500000");

    service.calculateTheMissingMonths();
    service.calculateTheMissingMonths();

    var stored = repository.findLatest(TKF100, SEPTEMBER).orElseThrow();
    assertThat(stored.complete()).isFalse();
    assertThat(stored.refusalReasons()).hasSize(1);
    assertThat(storedVersions()).isEqualTo(9);
    assertThat(storedRows("investment_kid_scenario")).isZero();
    assertThat(storedRows("investment_kid_cost")).isZero();
  }

  @Test
  void aRefusedMonthIsCalculatedOnTheNextRunOnceItsPricesAreFixed() {
    var closes = SyntheticIndexSeries.weekdayCloses("MSCI_ACWI");
    fundValues.saveAll(closes.stream().filter(close -> !inTheMarch2023Gap(close.date())).toList());
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 18), "0.00500000");
    service.calculateTheMissingMonths();
    fundValues.saveAll(closes.stream().filter(close -> inTheMarch2023Gap(close.date())).toList());

    service.calculateTheMissingMonths();
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 25), "0.04000000");
    service.calculateAndStore(TKF100, SEPTEMBER);

    assertThat(versionsOf(SEPTEMBER)).isEqualTo(3);
    assertThat(
            repository.findMonthsPublishedOrCompleteInTheirLatestVersion(
                TKF100, SEPTEMBER, SEPTEMBER))
        .containsExactly(SEPTEMBER);
  }

  @Test
  void aRecalculationIsANewVersionBesideTheEarlierOne() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 18), "0.00500000");
    service.calculateTheMissingMonths();
    var first = repository.findLatest(TKF100, SEPTEMBER).orElseThrow();
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 25), "0.04000000");

    var recalculated = service.calculateAndStore(TKF100, SEPTEMBER);

    assertThat(first.version()).isEqualTo(1);
    assertThat(recalculated.version()).isEqualTo(2);
    assertThat(recalculated.complete()).isTrue();
    assertThat(requireNonNull(recalculated.ocf()).ongoingCharges())
        .isEqualTo(new BigDecimal("0.04000000"));
    assertThat(versionsOf(SEPTEMBER)).isEqualTo(2);
  }

  @Test
  void aBackfilledMonthWithNoOcfInForceAtItsEndIsStoredAsARefusalWithNoNumbers() {
    fundValues.saveAll(SyntheticIndexSeries.weekdayCloses("MSCI_ACWI"));
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 18), "0.00500000");

    var stored = service.calculateAndStore(TKF100, YearMonth.of(2026, 6));

    assertThat(stored.complete()).isFalse();
    assertThat(stored.ocf()).isNull();
    assertThat(stored.refusalReasons()).hasSize(1);
    assertThat(storedRows("investment_kid_scenario")).isZero();
  }

  @Test
  void aBackfillNamesEveryMissingInput() {
    fundValues.saveAll(
        SyntheticIndexSeries.weekdayCloses("MSCI_ACWI").stream()
            .filter(close -> close.date().isBefore(LocalDate.of(2026, 9, 28)))
            .toList());

    var stored = service.calculateAndStore(TKF100, SEPTEMBER);

    assertThat(stored.refusalReasons()).hasSize(2);
  }

  @Test
  void aBackfilledPastMonthWithCompleteDataIsCalculatedWhileTheFeedHasStoppedToday() {
    fundValues.saveAll(
        SyntheticIndexSeries.weekdayCloses("MSCI_ACWI").stream()
            .filter(close -> close.date().isBefore(LocalDate.of(2026, 10, 9)))
            .toList());
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 18), "0.00500000");

    var stored = service.calculateAndStore(TKF100, SEPTEMBER);

    assertThat(stored.complete()).isTrue();
    assertThat(stored.lastPriceDate()).isEqualTo(LocalDate.of(2026, 9, 30));
  }

  @Test
  void aBackfillOfASeriesWithNoPricesIsARefusal() {
    KidOcfFigures.inForceFrom(jdbcClient, LocalDate.of(2026, 9, 18), "0.00500000");

    var stored = service.calculateAndStore(TKF100, SEPTEMBER);

    assertThat(stored.complete()).isFalse();
    assertThat(stored.refusalReasons()).hasSize(1);
  }

  @Test
  void refusesToBackfillAMonthBeforeTheHistoryStartsOrOneNotEndedYet() {
    assertThatThrownBy(() -> service.calculateAndStore(TKF100, YearMonth.of(2025, 12)))
        .isInstanceOf(InvalidKidRequestException.class);
    assertThatThrownBy(() -> service.calculateAndStore(TKF100, YearMonth.of(2026, 10)))
        .isInstanceOf(InvalidKidRequestException.class);
    assertThat(storedVersions()).isZero();
  }

  @Test
  void refusesToBackfillAFundWithNoKid() {
    assertThatThrownBy(() -> service.calculateAndStore(TUK75, SEPTEMBER))
        .isInstanceOf(InvalidKidRequestException.class);
    assertThat(storedVersions()).isZero();
  }

  private static boolean inTheMarch2023Gap(LocalDate date) {
    return !date.isBefore(LocalDate.of(2023, 3, 1)) && !date.isAfter(LocalDate.of(2023, 3, 20));
  }

  private CalculatedKidFigure kidFigureCalculatedFrom(
      YearMonth snapshotMonth, LocalDate validFrom) {
    var snapshotKey = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_snapshot
              (fund_code, snapshot_month, management_fee_rate, depot_fee_rate,
               underlying_fund_cost, transaction_cost_rate, total_ocf, complete, published_at,
               published_in)
            VALUES
              ('TKF100', :snapshotMonth, 0.00300000, 0.00035000, 0.00060000, 0.00005000,
               0.00400000, TRUE, CURRENT_TIMESTAMP, :publishedIn)
            """)
        .param("snapshotMonth", snapshotMonth.atDay(1))
        .param("publishedIn", "KID " + validFrom)
        .update(snapshotKey, "id");
    var snapshotId = requireNonNull(snapshotKey.getKey()).longValue();
    var publicationKey = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_publication
              (fund_code, valid_from, published_ocf, published_decimals, basis, basis_reason,
               snapshot_id, management_fee_rate, management_fee_rate_id, published_in,
               published_by)
            VALUES
              ('TKF100', :validFrom, 0.00400000, 2, 'MONTHLY_ESTIMATE', 'the monthly estimate',
               :snapshotId, 0.00300000, 7, :publishedIn, 'portfolio-manager')
            """)
        .param("validFrom", validFrom)
        .param("snapshotId", snapshotId)
        .param("publishedIn", "KID " + validFrom)
        .update(publicationKey, "id");
    return new CalculatedKidFigure(requireNonNull(publicationKey.getKey()).longValue(), snapshotId);
  }

  private record CalculatedKidFigure(long publicationId, long snapshotId) {}

  private long calculationIdOf(YearMonth month) {
    return jdbcClient
        .sql("SELECT id FROM investment_kid_calculation WHERE calculation_month = :month")
        .param("month", month.atDay(1))
        .query(Long.class)
        .single();
  }

  private String storedOcfRateSourceOf(YearMonth month) {
    return jdbcClient
        .sql(
            """
            SELECT ocf_rate_source FROM investment_kid_calculation
            WHERE calculation_month = :month AND complete = TRUE
            """)
        .param("month", month.atDay(1))
        .query(String.class)
        .single();
  }

  private long versionsOf(YearMonth month) {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM investment_kid_calculation WHERE calculation_month = :month")
        .param("month", month.atDay(1))
        .query(Long.class)
        .single();
  }

  private List<YearMonth> storedMonthsInTheOrderStored() {
    return jdbcClient
        .sql("SELECT calculation_month FROM investment_kid_calculation ORDER BY id")
        .query(LocalDate.class)
        .list()
        .stream()
        .map(YearMonth::from)
        .toList();
  }

  private long storedVersions() {
    return storedRows("investment_kid_calculation");
  }

  private long storedRows(String table) {
    return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
  }

  @TestConfiguration
  static class ClockForTheTest {
    @Bean
    SettableClock clock() {
      return new SettableClock();
    }
  }

  static class SettableClock extends Clock {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    private Instant instant = NOW;

    void set(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return TALLINN;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
