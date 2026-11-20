package ee.tuleva.onboarding.taxwin;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;

public record DeclaredMonth(BigDecimal income, BigDecimal withheldContributions) {

  public static final DeclaredMonth NOTHING_DECLARED = new DeclaredMonth(ZERO, ZERO);

  public DeclaredMonth plus(DeclaredMonth other) {
    return new DeclaredMonth(
        income.add(other.income), withheldContributions.add(other.withheldContributions));
  }
}
