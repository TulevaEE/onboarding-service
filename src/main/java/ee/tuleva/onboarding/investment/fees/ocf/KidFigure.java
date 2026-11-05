package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KidFigure(
    long publicationId,
    LocalDate validFrom,
    BigDecimal publishedOcf,
    int publishedDecimals,
    OcfPublicationBasis basis,
    String publishedIn) {

  PublishedPrecision precision() {
    return new PublishedPrecision(publishedDecimals);
  }
}
