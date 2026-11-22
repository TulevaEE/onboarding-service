package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.TransactionCostSource.PUBLISHED_OCF;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.series.SplicedPriceSeries;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@RequiredArgsConstructor
class KidCalculationRepository {

  private static final String REFUSAL_REASON_SEPARATOR = "\n";
  private static final String IS_THE_MONTHS_LATEST_VERSION =
      """
      NOT EXISTS (
        SELECT 1 FROM investment_kid_calculation later
        WHERE later.fund_code = c.fund_code
          AND later.calculation_month = c.calculation_month
          AND later.version > c.version)
      """;

  private final JdbcClient jdbcClient;
  private final TransactionTemplate transactionTemplate;

  StoredKidCalculation save(KidCalculation calculation, Instant calculatedAt) {
    var id =
        requireNonNull(
            transactionTemplate.execute(status -> insert(calculation, calculatedAt)),
            "KID calculation insert returned no id");
    return findById(id).orElseThrow();
  }

  Optional<StoredKidCalculation> findById(long id) {
    return jdbcClient
        .sql("SELECT * FROM investment_kid_calculation WHERE id = :id")
        .param("id", id)
        .query(KidCalculationRepository::stored)
        .optional();
  }

  Set<YearMonth> findMonthsPublishedOrCompleteInTheirLatestVersion(
      TulevaFund fund, YearMonth from, YearMonth through) {
    return jdbcClient
        .sql(
            """
            SELECT c.calculation_month FROM investment_kid_calculation c
            WHERE c.fund_code = :fundCode
              AND c.calculation_month BETWEEN :from AND :through
              AND %s
              AND (c.complete = TRUE
                   OR EXISTS (
                     SELECT 1 FROM investment_kid_publication p
                     WHERE p.fund_code = c.fund_code
                       AND p.calculation_month = c.calculation_month))
            """
                .formatted(IS_THE_MONTHS_LATEST_VERSION))
        .param("fundCode", fund.getCode())
        .param("from", from.atDay(1))
        .param("through", through.atDay(1))
        .query(LocalDate.class)
        .list()
        .stream()
        .map(YearMonth::from)
        .collect(toSet());
  }

  List<StoredKidCalculation> findLatestVersions(
      TulevaFund fund, YearMonth from, YearMonth through) {
    return jdbcClient
        .sql(
            """
            SELECT c.* FROM investment_kid_calculation c
            WHERE c.fund_code = :fundCode
              AND c.calculation_month BETWEEN :from AND :through
              AND %s
            ORDER BY c.calculation_month
            """
                .formatted(IS_THE_MONTHS_LATEST_VERSION))
        .param("fundCode", fund.getCode())
        .param("from", from.atDay(1))
        .param("through", through.atDay(1))
        .query(KidCalculationRepository::stored)
        .list();
  }

  List<StoredKidCalculation> findCompleteUnpublishedMonths(TulevaFund fund) {
    return jdbcClient
        .sql(
            """
            SELECT c.* FROM investment_kid_calculation c
            WHERE c.fund_code = :fundCode AND c.complete = TRUE
              AND %s
              AND NOT EXISTS (
                SELECT 1 FROM investment_kid_publication p
                WHERE p.fund_code = c.fund_code AND p.calculation_month = c.calculation_month)
            ORDER BY c.calculation_month
            """
                .formatted(IS_THE_MONTHS_LATEST_VERSION))
        .param("fundCode", fund.getCode())
        .query(KidCalculationRepository::stored)
        .list();
  }

  Optional<StoredKidCalculation> findLatest(TulevaFund fund, YearMonth month) {
    return jdbcClient
        .sql(
            """
            SELECT c.* FROM investment_kid_calculation c
            WHERE c.fund_code = :fundCode AND c.calculation_month = :month
              AND %s
            """
                .formatted(IS_THE_MONTHS_LATEST_VERSION))
        .param("fundCode", fund.getCode())
        .param("month", month.atDay(1))
        .query(KidCalculationRepository::stored)
        .optional();
  }

  private long insert(KidCalculation calculation, Instant calculatedAt) {
    var calculationId = insertHeader(calculation, calculatedAt);
    var figures = calculation.figures();
    if (figures != null) {
      figures.scenarios().forEach(scenario -> insertScenario(calculationId, scenario));
      figures.costs().forEach(cost -> insertCost(calculationId, cost));
    }
    return calculationId;
  }

  private long insertHeader(KidCalculation calculation, Instant calculatedAt) {
    var figures = calculation.figures();
    var ocf = calculation.ocf();
    var calculatedFrom = ocf == null ? null : ocf.calculatedFrom();
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_kid_calculation
              (fund_code, calculation_month, version, complete, refusal_reasons, series_segments,
               last_price_date, ocf_publication_id, ocf_valid_from, ocf_snapshot_id,
               ocf_snapshot_month, ongoing_charges, ocf_rate_source, transaction_cost_source,
               calculated_at)
            VALUES
              (:fundCode, :month, :version, :complete, :refusalReasons, :seriesSegments,
               :lastPriceDate, :ocfPublicationId, :ocfValidFrom, :ocfSnapshotId,
               :ocfSnapshotMonth, :ongoingCharges, :ocfRateSource, :transactionCostSource,
               :calculatedAt)
            """)
        .param("fundCode", calculation.fund().getCode())
        .param("month", calculation.month().atDay(1))
        .param("version", nextVersion(calculation))
        .param("complete", calculation.complete())
        .param("refusalReasons", joined(calculation.refusalReasons()))
        .param(
            "seriesSegments", valueOf(figures, stored -> SplicedPriceSeries.key(stored.series())))
        .param("lastPriceDate", valueOf(figures, KidFigures::lastPriceDate))
        .param("ocfPublicationId", valueOf(ocf, OcfBasis::publicationId))
        .param("ocfValidFrom", valueOf(ocf, OcfBasis::validFrom))
        .param("ocfSnapshotId", valueOf(calculatedFrom, OcfBasis.CalculatedFrom::snapshotId))
        .param("ocfSnapshotMonth", valueOf(calculatedFrom, from -> from.month().atDay(1)))
        .param("ongoingCharges", valueOf(ocf, OcfBasis::ongoingCharges))
        .param("ocfRateSource", valueOf(ocf, basis -> basis.rateSource().name()))
        .param("transactionCostSource", PUBLISHED_OCF.name())
        .param("calculatedAt", Timestamp.from(calculatedAt))
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey(), "KID calculation insert returned no id").longValue();
  }

  private int nextVersion(KidCalculation calculation) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(MAX(version), 0) + 1 FROM investment_kid_calculation
            WHERE fund_code = :fundCode AND calculation_month = :month
            """)
        .param("fundCode", calculation.fund().getCode())
        .param("month", calculation.month().atDay(1))
        .query(Integer.class)
        .single();
  }

  private void insertScenario(long calculationId, KidScenario scenario) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_kid_scenario
              (calculation_id, horizon_years, kind, window_source, window_start, window_end,
               window_months, unrounded_amount_eur, printed_amount_eur,
               unrounded_average_return_per_year, printed_average_return_per_year)
            VALUES
              (:calculationId, :horizonYears, :kind, :windowSource, :windowStart, :windowEnd,
               :windowMonths, :unroundedAmountEur, :printedAmountEur,
               :unroundedAverageReturnPerYear, :printedAverageReturnPerYear)
            """)
        .param("calculationId", calculationId)
        .param("horizonYears", scenario.horizonYears())
        .param("kind", scenario.kind().name())
        .param("windowSource", scenario.window().name())
        .param("windowStart", scenario.periodStart())
        .param("windowEnd", scenario.periodEnd())
        .param("windowMonths", scenario.windowMonths())
        .param("unroundedAmountEur", scenario.unroundedAmountEur())
        .param("printedAmountEur", scenario.amountEur())
        .param("unroundedAverageReturnPerYear", scenario.averageReturnPerYear())
        .param("printedAverageReturnPerYear", scenario.printedAverageReturnPerYear())
        .update();
  }

  private void insertCost(long calculationId, KidCost cost) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_kid_cost
              (calculation_id, horizon_years, unrounded_total_costs_eur, printed_total_costs_eur,
               unrounded_annual_cost_impact, printed_annual_cost_impact,
               unrounded_return_per_year_before_costs, printed_return_per_year_before_costs,
               unrounded_return_per_year_after_costs, printed_return_per_year_after_costs)
            VALUES
              (:calculationId, :horizonYears, :unroundedTotalCostsEur, :printedTotalCostsEur,
               :unroundedAnnualCostImpact, :printedAnnualCostImpact,
               :unroundedReturnPerYearBeforeCosts, :printedReturnPerYearBeforeCosts,
               :unroundedReturnPerYearAfterCosts, :printedReturnPerYearAfterCosts)
            """)
        .param("calculationId", calculationId)
        .param("horizonYears", cost.horizonYears())
        .param("unroundedTotalCostsEur", cost.unroundedTotalCostsEur())
        .param("printedTotalCostsEur", cost.totalCostsEur())
        .param("unroundedAnnualCostImpact", cost.unroundedAnnualCostImpact())
        .param("printedAnnualCostImpact", cost.annualCostImpact())
        .param("unroundedReturnPerYearBeforeCosts", cost.unroundedReturnPerYearBeforeCosts())
        .param("printedReturnPerYearBeforeCosts", cost.returnPerYearBeforeCosts())
        .param("unroundedReturnPerYearAfterCosts", cost.unroundedReturnPerYearAfterCosts())
        .param("printedReturnPerYearAfterCosts", cost.returnPerYearAfterCosts())
        .update();
  }

  private static <S, V> @Nullable V valueOf(@Nullable S source, Function<S, V> value) {
    return source == null ? null : value.apply(source);
  }

  private static @Nullable String joined(List<String> refusalReasons) {
    return refusalReasons.isEmpty() ? null : String.join(REFUSAL_REASON_SEPARATOR, refusalReasons);
  }

  private static StoredKidCalculation stored(ResultSet rs, int rowNum) throws SQLException {
    var refusalReasons = rs.getString("refusal_reasons");
    var lastPriceDate = rs.getObject("last_price_date", LocalDate.class);
    return new StoredKidCalculation(
        rs.getLong("id"),
        TulevaFund.fromCode(rs.getString("fund_code")),
        YearMonth.from(rs.getObject("calculation_month", LocalDate.class)),
        rs.getInt("version"),
        refusalReasons == null
            ? List.of()
            : List.of(refusalReasons.split(REFUSAL_REASON_SEPARATOR)),
        rs.getString("series_segments"),
        lastPriceDate,
        ocfBasis(rs),
        TransactionCostSource.valueOf(rs.getString("transaction_cost_source")),
        rs.getTimestamp("calculated_at").toInstant());
  }

  private static @Nullable OcfBasis ocfBasis(ResultSet rs) throws SQLException {
    var publicationId = rs.getObject("ocf_publication_id", Long.class);
    if (publicationId == null) {
      return null;
    }
    var snapshotId = rs.getObject("ocf_snapshot_id", Long.class);
    return new OcfBasis(
        publicationId,
        rs.getObject("ocf_valid_from", LocalDate.class),
        rs.getBigDecimal("ongoing_charges"),
        snapshotId == null
            ? null
            : new OcfBasis.CalculatedFrom(
                snapshotId, YearMonth.from(rs.getObject("ocf_snapshot_month", LocalDate.class))));
  }
}
