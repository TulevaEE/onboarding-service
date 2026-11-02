package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcfCalculationDecisionService {

  private static final String EXCEPTION_NOT_RECORDED = "OCF exception not recorded, ";
  private static final String REVIEW_NOT_RECORDED = "KID review not recorded, ";

  private final OcfCalculationVersions calculationVersions;
  private final KidComparisons kidComparisons;
  private final OcfExceptionRepository exceptionRepository;
  private final OcfKidReviewRepository kidReviewRepository;
  private final Clock clock;

  public OcfCalculationPreview preview(TulevaFund fund, YearMonth month) {
    return previewOf(fund, calculationVersions.latest(fund, month));
  }

  public OcfCalculationPreview recordException(OcfExceptionRequest request) {
    var snapshot = calculationVersions.unchanged(request);
    if (snapshot.publishedAt() != null) {
      throw request.refusal(EXCEPTION_NOT_RECORDED + "the calculation is already published");
    }
    if (snapshot.complete()) {
      throw request.refusal(EXCEPTION_NOT_RECORDED + "a complete calculation needs no exception");
    }
    if (exceptionRepository.findForTheCurrentCalculation(snapshot).isPresent()) {
      throw request.refusal(EXCEPTION_NOT_RECORDED + "one is already recorded for it");
    }
    exceptionRepository.record(
        snapshot, new ExceptionDecision(request.decidedBy(), request.reason(), clock.instant()));
    log.info(
        "OCF exception recorded: fund={}, month={}, version={}, checks={}",
        request.fund().getCode(),
        request.month(),
        request.version(),
        snapshot.checks());
    return previewOf(request.fund(), snapshot);
  }

  public OcfCalculationPreview recordReview(OcfKidReviewRequest request) {
    var snapshot = calculationVersions.unchanged(request);
    var comparison = kidComparisons.atTheMonthEndOf(request.fund(), snapshot);
    var kid = comparison.kidInForce();
    if (kid == null) {
      throw request.refusal(REVIEW_NOT_RECORDED + "no KID is recorded in force at the month end");
    }
    if (kid.publicationId() != request.publicationId()) {
      throw request.refusal(
          REVIEW_NOT_RECORDED
              + "the KID in force at the month end is no longer the one reviewed; a newer KID was"
              + " recorded, look at the calculation again");
    }
    if (!differsFromTheKid(snapshot, comparison)) {
      throw request.refusal(
          REVIEW_NOT_RECORDED
              + "a complete calculation differing from the KID at the published precision is"
              + " what is reviewed");
    }
    kidReviewRepository.record(
        snapshot, kid, new KidReview(request.reviewedBy(), request.reason(), clock.instant()));
    log.info(
        "KID kept on review: fund={}, month={}, version={}, totalOcf={}, publicationId={}",
        request.fund().getCode(),
        request.month(),
        request.version(),
        snapshot.totalOcf(),
        kid.publicationId());
    return previewOf(request.fund(), snapshot);
  }

  private OcfCalculationPreview previewOf(TulevaFund fund, OcfSnapshot snapshot) {
    var comparison = kidComparisons.atTheMonthEndOf(fund, snapshot);
    return new OcfCalculationPreview(
        fund.getCode(),
        YearMonth.from(snapshot.snapshotMonth()),
        snapshot.version(),
        snapshot.complete(),
        snapshot.checks(),
        OcfCalculationFingerprint.of(snapshot),
        snapshot.totalOcf(),
        snapshot.managementFeeRate(),
        comparison.precision().decimals(),
        comparison.kidInForce(),
        differsFromTheKid(snapshot, comparison),
        exceptionRepository.findForTheCurrentCalculation(snapshot).orElse(null),
        comparison.keptOnReview(),
        snapshot.publishedAt() != null);
  }

  private static boolean differsFromTheKid(OcfSnapshot snapshot, KidComparison comparison) {
    return snapshot.complete() && comparison.differsFrom(snapshot.totalOcf());
  }
}
