package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcfCalculationDecisionService {

  private static final String EXCEPTION_NOT_RECORDED = "OCF exception not recorded, ";
  private static final String REVIEW_NOT_RECORDED = "KID review not recorded, ";
  private static final String ALREADY_EXCEPTED =
      EXCEPTION_NOT_RECORDED + "one is already recorded for it";
  private static final String MONTH_ALREADY_PUBLISHED =
      REVIEW_NOT_RECORDED + "a calculation of the month is already published as a KID";

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
      throw request.refusal(ALREADY_EXCEPTED);
    }
    try {
      exceptionRepository.record(
          snapshot, new ExceptionDecision(request.decidedBy(), request.reason(), clock.instant()));
    } catch (DuplicateKeyException exceptedConcurrently) {
      throw request.refusal(ALREADY_EXCEPTED, exceptedConcurrently);
    }
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
    var kid =
        reviewableKid(request, snapshot, kidComparisons.atTheMonthEndOf(request.fund(), snapshot));
    if (!kidReviewRepository.recordUnlessPublishedOrRecalculated(
        snapshot, kid, new KidReview(request.reviewedBy(), request.reason(), clock.instant()))) {
      throw calculationVersions.anyVersionPublished(request.fund(), request.month())
          ? request.refusal(MONTH_ALREADY_PUBLISHED)
          : request.recalculatedSincePreviewed();
    }
    log.info(
        "KID kept on review: fund={}, month={}, version={}, totalOcf={}, publicationId={}",
        request.fund().getCode(),
        request.month(),
        request.version(),
        snapshot.totalOcf(),
        kid.publicationId());
    return previewOf(request.fund(), snapshot);
  }

  private static KidFigure reviewableKid(
      OcfKidReviewRequest request, OcfSnapshot snapshot, KidComparison comparison) {
    if (comparison.monthPublished()) {
      throw request.refusal(MONTH_ALREADY_PUBLISHED);
    }
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
    if (!comparison.differsFrom(snapshot)) {
      throw request.refusal(
          REVIEW_NOT_RECORDED
              + "a complete calculation differing from the KID at the published precision is"
              + " what is reviewed");
    }
    return kid;
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
        comparison.differsFrom(snapshot),
        exceptionRepository.findForTheCurrentCalculation(snapshot).orElse(null),
        comparison.keptOnReview(),
        snapshot.publishedAt() != null);
  }
}
