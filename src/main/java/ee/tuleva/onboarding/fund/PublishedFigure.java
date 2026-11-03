package ee.tuleva.onboarding.fund;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;

public record PublishedFigure(BigDecimal figure, int decimals) {

  private static final int FRACTION_TO_PERCENT = 2;

  public BigDecimal percent() {
    return figure.movePointRight(FRACTION_TO_PERCENT).setScale(decimals, HALF_UP);
  }

  PublishedFigure withoutTrailingZeros() {
    return new PublishedFigure(figure.stripTrailingZeros(), decimals);
  }
}
