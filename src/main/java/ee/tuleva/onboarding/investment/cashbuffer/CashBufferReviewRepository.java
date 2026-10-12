package ee.tuleva.onboarding.investment.cashbuffer;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class CashBufferReviewRepository {

  private final JdbcClient jdbcClient;

  @Transactional
  void save(CashBufferReview review) {
    deleteEarlierRunFor(review.fund(), review.reviewMonth());
    var reviewId = insertReview(review);
    review.window().months().forEach(month -> insertMonth(reviewId, month));
  }

  boolean existsFor(TulevaFund fund, YearMonth reviewMonth) {
    return idOf(fund, reviewMonth).isPresent();
  }

  Optional<CashBufferReview> findByFundAndMonth(TulevaFund fund, YearMonth reviewMonth) {
    return idOf(fund, reviewMonth).map(this::findById);
  }

  private Optional<Long> idOf(TulevaFund fund, YearMonth reviewMonth) {
    return jdbcClient
        .sql(
            """
            SELECT id FROM investment_cash_buffer_review
            WHERE fund_code = :fundCode AND review_month = :reviewMonth
            """)
        .param("fundCode", fund.name())
        .param("reviewMonth", reviewMonth.atDay(1))
        .query(Long.class)
        .optional();
  }

  private CashBufferReview findById(long reviewId) {
    var months = monthsOf(reviewId);
    return jdbcClient
        .sql("SELECT * FROM investment_cash_buffer_review WHERE id = :reviewId")
        .param("reviewId", reviewId)
        .query((rs, rowNum) -> toReview(rs, months))
        .single();
  }

  private void deleteEarlierRunFor(TulevaFund fund, YearMonth reviewMonth) {
    jdbcClient
        .sql(
            """
            DELETE FROM investment_cash_buffer_review_month
            WHERE review_id IN (
              SELECT id FROM investment_cash_buffer_review
              WHERE fund_code = :fundCode AND review_month = :reviewMonth)
            """)
        .param("fundCode", fund.name())
        .param("reviewMonth", reviewMonth.atDay(1))
        .update();
    jdbcClient
        .sql(
            """
            DELETE FROM investment_cash_buffer_review
            WHERE fund_code = :fundCode AND review_month = :reviewMonth
            """)
        .param("fundCode", fund.name())
        .param("reviewMonth", reviewMonth.atDay(1))
        .update();
  }

  private long insertReview(CashBufferReview review) {
    var recommendation = review.recommendation();
    var model = recommendation.model();
    var window = review.window();
    var softDrift = review.softDrift();
    var hardDrift = review.hardDrift();
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_cash_buffer_review (
              fund_code, review_month, reviewed_on, window_start_month, window_months,
              outflow_percentile, outflow_at_percentile, inflow_percentile, inflow_at_percentile,
              inflow_credit, settlement_horizon_days, horizon_outflow_at_percentile, accrued_fees,
              recommended_soft, recommended_hard,
              configured_limit_effective_date, configured_reserve_soft, configured_reserve_hard,
              drift_threshold, sustain_runs,
              soft_divergence, soft_drifted, soft_consecutive_drifted_runs,
              hard_divergence, hard_drifted, hard_consecutive_drifted_runs,
              unrecognised_payouts, unrecognised_outflow)
            VALUES (
              :fundCode, :reviewMonth, :reviewedOn, :windowStartMonth, :windowMonths,
              :outflowPercentile, :outflowAtPercentile, :inflowPercentile, :inflowAtPercentile,
              :inflowCredit, :settlementHorizonDays, :horizonOutflowAtPercentile, :accruedFees,
              :recommendedSoft, :recommendedHard,
              :configuredLimitEffectiveDate, :configuredReserveSoft, :configuredReserveHard,
              :driftThreshold, :sustainRuns,
              :softDivergence, :softDrifted, :softConsecutiveDriftedRuns,
              :hardDivergence, :hardDrifted, :hardConsecutiveDriftedRuns,
              :unrecognisedPayouts, :unrecognisedOutflow)
            """)
        .param("fundCode", review.fund().name())
        .param("reviewMonth", review.reviewMonth().atDay(1))
        .param("reviewedOn", review.reviewedOn())
        .param("windowStartMonth", window.firstMonth().atDay(1))
        .param("windowMonths", window.depth())
        .param("outflowPercentile", model.outflowPercentile())
        .param("outflowAtPercentile", recommendation.outflowAtPercentile())
        .param("inflowPercentile", model.inflowPercentile())
        .param("inflowAtPercentile", recommendation.inflowAtPercentile())
        .param("inflowCredit", model.inflowCredit())
        .param("settlementHorizonDays", model.settlementHorizonDays())
        .param("horizonOutflowAtPercentile", recommendation.horizonOutflowAtPercentile())
        .param("accruedFees", recommendation.accruedFees())
        .param("recommendedSoft", recommendation.recommendedSoft())
        .param("recommendedHard", recommendation.recommendedHard())
        .param("configuredLimitEffectiveDate", review.configured().effectiveDate())
        .param("configuredReserveSoft", review.configured().reserveSoft())
        .param("configuredReserveHard", review.configured().reserveHard())
        .param("driftThreshold", softDrift.threshold())
        .param("sustainRuns", softDrift.sustainRuns())
        .param("softDivergence", softDrift.divergence())
        .param("softDrifted", softDrift.drifted())
        .param("softConsecutiveDriftedRuns", softDrift.consecutiveRuns())
        .param("hardDivergence", hardDrift == null ? null : hardDrift.divergence())
        .param("hardDrifted", hardDrift == null ? null : hardDrift.drifted())
        .param("hardConsecutiveDriftedRuns", hardDrift == null ? null : hardDrift.consecutiveRuns())
        .param("unrecognisedPayouts", window.unrecognisedPayouts())
        .param("unrecognisedOutflow", window.unrecognisedOutflow())
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey(), "Cash buffer review insert returned no id")
        .longValue();
  }

  private void insertMonth(long reviewId, MonthlyFlows month) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_cash_buffer_review_month (
              review_id, flow_month, inflow, recurring_outflow, tail_outflow, cycle_outflow,
              unrecognised_outflow, unrecognised_payouts)
            VALUES (
              :reviewId, :flowMonth, :inflow, :recurringOutflow, :tailOutflow, :cycleOutflow,
              :unrecognisedOutflow, :unrecognisedPayouts)
            """)
        .param("reviewId", reviewId)
        .param("flowMonth", month.month().atDay(1))
        .param("inflow", month.inflow())
        .param("recurringOutflow", month.recurringOutflow())
        .param("tailOutflow", month.tailOutflow())
        .param("cycleOutflow", month.cycleOutflow())
        .param("unrecognisedOutflow", month.unrecognisedOutflow())
        .param("unrecognisedPayouts", month.unrecognisedPayouts())
        .update();
  }

  private List<MonthlyFlows> monthsOf(long reviewId) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_cash_buffer_review_month
            WHERE review_id = :reviewId
            ORDER BY flow_month
            """)
        .param("reviewId", reviewId)
        .query(
            (rs, rowNum) ->
                new MonthlyFlows(
                    YearMonth.from(localDate(rs, "flow_month")),
                    rs.getBigDecimal("inflow"),
                    rs.getBigDecimal("recurring_outflow"),
                    rs.getBigDecimal("tail_outflow"),
                    rs.getBigDecimal("cycle_outflow"),
                    rs.getBigDecimal("unrecognised_outflow"),
                    rs.getInt("unrecognised_payouts")))
        .list();
  }

  private static CashBufferReview toReview(ResultSet rs, List<MonthlyFlows> months)
      throws SQLException {
    return new CashBufferReview(
        TulevaFund.valueOf(rs.getString("fund_code")),
        YearMonth.from(localDate(rs, "review_month")),
        localDate(rs, "reviewed_on"),
        new FlowWindow(months),
        new Recommendation(
            new BufferModel(
                rs.getBigDecimal("outflow_percentile"),
                rs.getBigDecimal("inflow_percentile"),
                rs.getBigDecimal("inflow_credit"),
                rs.getInt("settlement_horizon_days")),
            rs.getBigDecimal("outflow_at_percentile"),
            rs.getBigDecimal("inflow_at_percentile"),
            rs.getBigDecimal("horizon_outflow_at_percentile"),
            rs.getBigDecimal("accrued_fees"),
            rs.getBigDecimal("recommended_soft"),
            rs.getBigDecimal("recommended_hard")),
        new ConfiguredReserve(
            localDate(rs, "configured_limit_effective_date"),
            rs.getBigDecimal("configured_reserve_soft"),
            rs.getBigDecimal("configured_reserve_hard")),
        new Drift(
            rs.getBigDecimal("soft_divergence"),
            rs.getBigDecimal("drift_threshold"),
            rs.getBoolean("soft_drifted"),
            rs.getInt("soft_consecutive_drifted_runs"),
            rs.getInt("sustain_runs")),
        hardDrift(rs));
  }

  private static @Nullable Drift hardDrift(ResultSet rs) throws SQLException {
    var divergence = rs.getBigDecimal("hard_divergence");
    if (divergence == null) {
      return null;
    }
    return new Drift(
        divergence,
        rs.getBigDecimal("drift_threshold"),
        rs.getBoolean("hard_drifted"),
        rs.getInt("hard_consecutive_drifted_runs"),
        rs.getInt("sustain_runs"));
  }

  private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
    return rs.getDate(column).toLocalDate();
  }
}
