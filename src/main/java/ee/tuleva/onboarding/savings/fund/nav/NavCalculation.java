package ee.tuleva.onboarding.savings.fund.nav;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NavCalculation(Instant calculatedAt, List<NavAccountLine> lines) {

  private static final List<String> CUSTODIAN_SOURCED_ACCOUNT_TYPES =
      List.of("CASH", "RECEIVABLES", "LIABILITY");
  private static final String SECURITY = "SECURITY";
  private static final String UNITS = "UNITS";

  public List<NavAccountLine> custodianComparableLines() {
    return lines.stream()
        .filter(line -> CUSTODIAN_SOURCED_ACCOUNT_TYPES.contains(line.accountType()))
        .filter(
            line -> !NavReportAccountNames.NOT_SOURCED_FROM_CUSTODIAN.contains(line.accountName()))
        .toList();
  }

  public List<NavAccountLine> securityLines() {
    return lines.stream().filter(line -> SECURITY.equals(line.accountType())).toList();
  }

  public BigDecimal assetsUnderManagement() {
    return lines.stream()
        .filter(line -> UNITS.equals(line.accountType()))
        .map(NavAccountLine::value)
        .reduce(ZERO, BigDecimal::add);
  }
}
