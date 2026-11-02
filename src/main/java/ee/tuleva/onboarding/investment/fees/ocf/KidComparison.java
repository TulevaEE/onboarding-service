package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

record KidComparison(
    PublishedPrecision fundRule, @Nullable KidFigure kidInForce, @Nullable KidReview keptOnReview) {

  PublishedPrecision precision() {
    return kidInForce == null ? fundRule : kidInForce.precision();
  }

  boolean differsFrom(BigDecimal ocf) {
    return kidInForce != null && kidInForce.precision().differ(kidInForce.publishedOcf(), ocf);
  }
}
