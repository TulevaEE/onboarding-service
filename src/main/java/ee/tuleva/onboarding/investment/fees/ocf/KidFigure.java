package ee.tuleva.onboarding.investment.fees.ocf;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KidFigure(
    LocalDate snapshotMonth,
    int version,
    LocalDate validFrom,
    BigDecimal publishedOcf,
    String publishedIn) {

  private static final int PUBLISHED_PERCENT_DECIMAL_PLACES = 2;
  private static final int PUBLISHED_FRACTION_DECIMAL_PLACES = PUBLISHED_PERCENT_DECIMAL_PLACES + 2;

  boolean differsAtThePublishedPrecisionFrom(BigDecimal ocf) {
    return publishedOcf
            .setScale(PUBLISHED_FRACTION_DECIMAL_PLACES, HALF_UP)
            .compareTo(ocf.setScale(PUBLISHED_FRACTION_DECIMAL_PLACES, HALF_UP))
        != 0;
  }
}
