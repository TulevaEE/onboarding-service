package ee.tuleva.onboarding.investment.kid;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class KidPrintedFiguresRepository {

  private final JdbcClient jdbcClient;

  Map<Long, List<PrintedScenario>> findPrintedScenarios(List<Long> calculationIds) {
    if (calculationIds.isEmpty()) {
      return Map.of();
    }
    return jdbcClient
        .sql(
            """
            SELECT calculation_id, horizon_years, kind, printed_amount_eur,
                   printed_average_return_per_year
            FROM investment_kid_scenario
            WHERE calculation_id IN (:calculationIds)
            """)
        .param("calculationIds", calculationIds)
        .query((rs, rowNum) -> Map.entry(rs.getLong("calculation_id"), PrintedScenario.fromRow(rs)))
        .list()
        .stream()
        .sorted(Map.Entry.comparingByValue(PrintedScenario.AS_THE_KID_PRINTS_THEM))
        .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));
  }

  Map<Long, List<PrintedCost>> findPrintedCosts(List<Long> calculationIds) {
    if (calculationIds.isEmpty()) {
      return Map.of();
    }
    return jdbcClient
        .sql(
            """
            SELECT calculation_id, horizon_years, printed_total_costs_eur,
                   printed_annual_cost_impact, printed_return_per_year_before_costs,
                   printed_return_per_year_after_costs
            FROM investment_kid_cost
            WHERE calculation_id IN (:calculationIds)
            ORDER BY calculation_id, horizon_years
            """)
        .param("calculationIds", calculationIds)
        .query(
            (rs, rowNum) ->
                Map.entry(
                    rs.getLong("calculation_id"),
                    new PrintedCost(
                        rs.getInt("horizon_years"),
                        rs.getBigDecimal("printed_total_costs_eur"),
                        rs.getBigDecimal("printed_annual_cost_impact"),
                        rs.getBigDecimal("printed_return_per_year_before_costs"),
                        rs.getBigDecimal("printed_return_per_year_after_costs"))))
        .list()
        .stream()
        .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));
  }
}
