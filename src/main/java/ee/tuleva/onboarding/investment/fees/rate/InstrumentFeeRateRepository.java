package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.InstrumentFeeAgreementRepository.THE_AGREEMENT_OF_EACH_ISIN_VALID_ON_THE_DATE;
import static java.util.Objects.requireNonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class InstrumentFeeRateRepository {

  private final JdbcClient jdbcClient;

  InstrumentRate append(ComputedRate rate) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee_rate
              (isin, period_start, period_end, published_ocf, rebate_rate, invoiced_fee_rate,
               net_ocf, rate_basis, fallback_reason, rebate_kind, volume_eur, volume_nav_date,
               eur_usd, instrument_fee_id)
            VALUES
              (:isin, :periodStart, :periodEnd, :publishedOcf, :rebateRate, :invoicedFeeRate,
               :netOcf, :rateBasis, :fallbackReason, :rebateKind, :volumeEur, :volumeNavDate,
               :eurUsd, :instrumentFeeId)
            """)
        .param("isin", rate.isin())
        .param("periodStart", rate.period().atDay(1))
        .param("periodEnd", rate.period().atEndOfMonth())
        .param("publishedOcf", rate.publishedOcf())
        .param("rebateRate", rate.rebateRate())
        .param("invoicedFeeRate", rate.invoicedFeeRate())
        .param("netOcf", rate.netOcf())
        .param("rateBasis", rate.rateBasis().name())
        .param("fallbackReason", rate.fallbackReason())
        .param("rebateKind", rate.rebateKind().name())
        .param("volumeEur", rate.volumeEur())
        .param("volumeNavDate", rate.volumeNavDate())
        .param("eurUsd", rate.eurUsd())
        .param("instrumentFeeId", rate.instrumentFeeId())
        .update(keyHolder, "id");
    var id = requireNonNull(keyHolder.getKey(), "Instrument fee rate insert returned no id");
    return new InstrumentRate(
        id.longValue(),
        rate.isin(),
        rate.period(),
        rate.publishedOcf(),
        rate.netOcf(),
        rate.rateBasis(),
        rate.fallbackReason(),
        rate.rebateKind());
  }

  List<InstrumentRate> findNewestFor(YearMonth period) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM (
              SELECT r.*,
                     ROW_NUMBER() OVER (PARTITION BY r.isin ORDER BY r.created_at DESC, r.id DESC) AS rn
              FROM investment_instrument_fee_rate r
              JOIN (%s) a ON a.id = r.instrument_fee_id
              WHERE r.period_start = :periodStart AND r.period_end = :periodEnd
            ) ranked
            WHERE rn = 1
            ORDER BY isin
            """
                .formatted(THE_AGREEMENT_OF_EACH_ISIN_VALID_ON_THE_DATE))
        .param("periodStart", period.atDay(1))
        .param("periodEnd", period.atEndOfMonth())
        .param("date", period.atEndOfMonth())
        .query((rs, rowNum) -> rate(rs, period))
        .list();
  }

  Set<Long> agreementsResolvedSince(YearMonth period, Instant since) {
    return Set.copyOf(
        jdbcClient
            .sql(
                """
                SELECT DISTINCT instrument_fee_id FROM investment_instrument_fee_rate
                WHERE period_start = :periodStart AND period_end = :periodEnd
                  AND created_at >= :since
                """)
            .param("periodStart", period.atDay(1))
            .param("periodEnd", period.atEndOfMonth())
            .param("since", Timestamp.from(since))
            .query(Long.class)
            .list());
  }

  private static InstrumentRate rate(ResultSet rs, YearMonth period) throws SQLException {
    return new InstrumentRate(
        rs.getLong("id"),
        rs.getString("isin"),
        period,
        rs.getBigDecimal("published_ocf"),
        rs.getBigDecimal("net_ocf"),
        RateBasis.valueOf(rs.getString("rate_basis")),
        rs.getString("fallback_reason"),
        RebateKind.valueOf(rs.getString("rebate_kind")));
  }
}
