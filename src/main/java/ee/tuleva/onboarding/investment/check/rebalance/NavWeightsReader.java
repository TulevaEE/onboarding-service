package ee.tuleva.onboarding.investment.check.rebalance;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NavWeightsReader {

  private static final String UNITS = "UNITS";

  private final JdbcClient jdbcClient;

  List<NavWeights> between(TulevaFund fund, LocalDate from, LocalDate to) {
    return jdbcClient
        .sql(
            """
            SELECT r.nav_date, r.account_type, r.account_id, r.market_value
            FROM nav_report r
            WHERE r.fund_code = :fundCode
              AND r.nav_date BETWEEN :from AND :to
              AND r.account_type IN ('SECURITY', 'UNITS')
              AND r.market_value IS NOT NULL
              AND r.calculation_id = (
                SELECT p.calculation_id FROM nav_report p
                WHERE p.fund_code = r.fund_code
                  AND p.nav_date = r.nav_date
                  AND p.published_at IS NOT NULL
                ORDER BY p.published_at DESC, p.id DESC
                LIMIT 1)
            """)
        .param("fundCode", fund.getCode())
        .param("from", from)
        .param("to", to)
        .query(NavWeightsReader::row)
        .list()
        .stream()
        .collect(groupingBy(NavRow::navDate, TreeMap::new, toList()))
        .entrySet()
        .stream()
        .flatMap(rowsOfADate -> measured(rowsOfADate.getKey(), rowsOfADate.getValue()).stream())
        .toList();
  }

  private static Optional<NavWeights> measured(LocalDate navDate, List<NavRow> rows) {
    return rows.stream()
        .filter(NavRow::isUnits)
        .map(NavRow::marketValue)
        .reduce(BigDecimal::add)
        .filter(netAssetValue -> netAssetValue.signum() > 0)
        .map(netAssetValue -> new NavWeights(navDate, netAssetValue, securities(rows)));
  }

  private static Map<String, BigDecimal> securities(List<NavRow> rows) {
    return rows.stream()
        .filter(NavRow::isSecurityWithAnIsin)
        .collect(
            toMap(row -> Objects.requireNonNull(row.isin()), NavRow::marketValue, BigDecimal::add));
  }

  private static NavRow row(ResultSet resultSet, int rowNum) throws SQLException {
    return new NavRow(
        resultSet.getObject("nav_date", LocalDate.class),
        resultSet.getString("account_type"),
        resultSet.getString("account_id"),
        resultSet.getBigDecimal("market_value"));
  }

  private record NavRow(
      LocalDate navDate, String accountType, @Nullable String isin, BigDecimal marketValue) {

    boolean isUnits() {
      return UNITS.equals(accountType);
    }

    boolean isSecurityWithAnIsin() {
      return !isUnits() && isin != null;
    }
  }
}
