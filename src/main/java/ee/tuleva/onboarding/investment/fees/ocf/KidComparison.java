package ee.tuleva.onboarding.investment.fees.ocf;

import org.jspecify.annotations.Nullable;

record KidComparison(
    PublishedPrecision fundRule,
    @Nullable KidFigure kidInForce,
    @Nullable KidReview keptOnReview,
    boolean monthPublished) {

  PublishedPrecision precision() {
    return kidInForce == null ? fundRule : kidInForce.precision();
  }

  boolean differsFrom(OcfSnapshot snapshot) {
    return !monthPublished
        && snapshot.complete()
        && kidInForce != null
        && kidInForce.precision().differ(kidInForce.publishedOcf(), snapshot.totalOcf());
  }

  KidComparison keptOn(KidReview review) {
    return new KidComparison(fundRule, kidInForce, review, monthPublished);
  }
}
