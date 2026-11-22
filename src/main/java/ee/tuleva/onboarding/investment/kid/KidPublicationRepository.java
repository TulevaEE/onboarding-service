package ee.tuleva.onboarding.investment.kid;

import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class KidPublicationRepository {

  private static final Comparator<PublishedScenario> NEWEST_MONTH_FIRST_THEN_AS_THE_KID_PRINTS =
      comparing(PublishedScenario::month)
          .reversed()
          .thenComparing(PublishedScenario::printed, PrintedScenario.AS_THE_KID_PRINTS_THEM);

  private final JdbcClient jdbcClient;

  boolean insertUnlessANewerVersionOfTheMonthExists(KidPublication publication) {
    return jdbcClient
            .sql(
                """
                INSERT INTO investment_kid_publication
                  (fund_code, calculation_month, calculation_id, published_by, published_at)
                SELECT :fundCode, :month, :calculationId, :publishedBy, :publishedAt
                WHERE NOT EXISTS (
                  SELECT 1 FROM investment_kid_calculation newer
                  WHERE newer.fund_code = :fundCode
                    AND newer.calculation_month = :month
                    AND newer.version > (
                      SELECT published.version FROM investment_kid_calculation published
                      WHERE published.id = :calculationId))
                """)
            .param("fundCode", publication.fund().getCode())
            .param("month", publication.month().atDay(1))
            .param("calculationId", publication.calculationId())
            .param("publishedBy", publication.publishedBy())
            .param("publishedAt", Timestamp.from(publication.publishedAt()))
            .update()
        == 1;
  }

  boolean isPublished(TulevaFund fund, YearMonth month) {
    return jdbcClient
            .sql(
                """
                SELECT COUNT(*) FROM investment_kid_publication
                WHERE fund_code = :fundCode AND calculation_month = :month
                """)
            .param("fundCode", fund.getCode())
            .param("month", month.atDay(1))
            .query(Long.class)
            .single()
        > 0;
  }

  Map<YearMonth, KidPublication> findPublications(
      TulevaFund fund, YearMonth from, YearMonth through) {
    return jdbcClient
        .sql(
            """
            SELECT calculation_month, calculation_id, published_by, published_at
            FROM investment_kid_publication
            WHERE fund_code = :fundCode AND calculation_month BETWEEN :from AND :through
            """)
        .param("fundCode", fund.getCode())
        .param("from", from.atDay(1))
        .param("through", through.atDay(1))
        .query(
            (rs, rowNum) ->
                new KidPublication(
                    fund,
                    YearMonth.from(rs.getObject("calculation_month", LocalDate.class)),
                    rs.getLong("calculation_id"),
                    rs.getString("published_by"),
                    rs.getTimestamp("published_at").toInstant()))
        .list()
        .stream()
        .collect(toMap(KidPublication::month, identity()));
  }

  List<PublishedScenario> findPublishedScenarios(TulevaFund fund) {
    return jdbcClient
        .sql(
            """
            SELECT p.calculation_month, s.horizon_years, s.kind, s.printed_amount_eur,
                   s.printed_average_return_per_year
            FROM investment_kid_publication p
            JOIN investment_kid_scenario s ON s.calculation_id = p.calculation_id
            WHERE p.fund_code = :fundCode
            """)
        .param("fundCode", fund.getCode())
        .query(
            (rs, rowNum) ->
                PublishedScenario.of(
                    YearMonth.from(rs.getObject("calculation_month", LocalDate.class)),
                    PrintedScenario.fromRow(rs)))
        .list()
        .stream()
        .sorted(NEWEST_MONTH_FIRST_THEN_AS_THE_KID_PRINTS)
        .toList();
  }
}
