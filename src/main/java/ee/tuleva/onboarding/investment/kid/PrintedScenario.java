package ee.tuleva.onboarding.investment.kid;

import static java.util.Comparator.comparing;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;

record PrintedScenario(
    int horizonYears,
    ScenarioKind scenario,
    BigDecimal amountEur,
    BigDecimal averageReturnPerYear) {

  static final Comparator<PrintedScenario> AS_THE_KID_PRINTS_THEM =
      comparing(PrintedScenario::horizonYears).thenComparing(PrintedScenario::scenario);

  static PrintedScenario fromRow(ResultSet rs) throws SQLException {
    return new PrintedScenario(
        rs.getInt("horizon_years"),
        ScenarioKind.valueOf(rs.getString("kind")),
        rs.getBigDecimal("printed_amount_eur"),
        rs.getBigDecimal("printed_average_return_per_year"));
  }
}
