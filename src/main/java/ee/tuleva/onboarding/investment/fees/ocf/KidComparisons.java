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

  KidComparison atTheMonthEndOf(TulevaFund fund, OcfSnapshot snapshot) {
    var kid =
        publicationRepository.findInForceOn(
            fund.getCode(), YearMonth.from(snapshot.snapshotMonth()).atEndOfMonth());
    return new KidComparison(
        PublishedPrecision.of(fund),
        kid.orElse(null),
        kid.flatMap(inForce -> kidReviewRepository.findLatestKeeping(inForce, snapshot.totalOcf()))
            .orElse(null));
  }
}
