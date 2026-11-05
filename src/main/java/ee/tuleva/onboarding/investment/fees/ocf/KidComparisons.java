package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class KidComparisons {

  private final OcfPublicationRepository publicationRepository;
  private final OcfKidReviewRepository kidReviewRepository;
  private final OcfSnapshotRepository ocfSnapshotRepository;

  KidComparison atTheMonthEndOf(TulevaFund fund, OcfSnapshot snapshot) {
    var kid =
        publicationRepository
            .findInForceOn(fund.getCode(), YearMonth.from(snapshot.snapshotMonth()).atEndOfMonth())
            .orElse(null);
    var unreviewed =
        new KidComparison(
            PublishedPrecision.of(fund),
            kid,
            null,
            ocfSnapshotRepository.anyVersionPublished(fund.getCode(), snapshot.snapshotMonth()));
    if (kid == null || !unreviewed.differsFrom(snapshot)) {
      return unreviewed;
    }
    return kidReviewRepository
        .findLatestKeeping(kid, snapshot.totalOcf())
        .map(unreviewed::keptOn)
        .orElse(unreviewed);
  }
}
