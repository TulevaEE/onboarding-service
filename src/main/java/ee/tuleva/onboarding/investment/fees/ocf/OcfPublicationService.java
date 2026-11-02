package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcfPublicationService {

  private static final String NOT_PUBLISHED = "OCF not published, ";

  private final OcfCalculationVersions calculationVersions;
  private final FeeRateRepository feeRateRepository;
  private final OcfPublicationRepository publicationRepository;
  private final OcfExceptionRepository exceptionRepository;

  public OcfPublicationPreview preview(TulevaFund fund, YearMonth month, LocalDate validFrom) {
    refuseUnlessAfterTheMonth(fund, month, validFrom);
    return preview(fund, calculationVersions.latest(fund, month), validFrom);
  }

  public OcfPublicationPreview publish(OcfPublicationRequest request) {
    var fund = request.fund();
    refuseUnlessAfterTheMonth(fund, request.month(), request.validFrom());
    var snapshot = calculationVersions.unchanged(request);
    var fee = managementFeeOn(fund, request.validFrom());
    var publishedOcf = publishedOcf(snapshot, fee);
    refuseUnlessPublishable(snapshot, request, publishedOcf);
    refuseUnlessTheIncompletenessWasExcepted(snapshot, request);
    var alreadyRecorded =
        NOT_PUBLISHED + kidAlreadyRecorded(request.validFrom(), request.publishedIn());
    if (publicationRepository.isRecorded(
        fund.getCode(), request.validFrom(), request.publishedIn())) {
      throw request.refusal(alreadyRecorded);
    }
    var publication = OcfPublication.fromCalculation(request, snapshot, publishedOcf, fee);
    if (!unlessRecordedMeanwhile(
        () -> publicationRepository.recordFromCalculation(snapshot, publication),
        recordedConcurrently -> request.refusal(alreadyRecorded, recordedConcurrently))) {
      throw request.refusal(NOT_PUBLISHED + "the calculation changed or was published since read");
    }
    log.info(
        "OCF published from a calculation: fund={}, month={}, version={}, validFrom={},"
            + " publishedOcf={}, publishedIn={}, complete={}",
        fund.getCode(),
        request.month(),
        request.version(),
        request.validFrom(),
        publishedOcf,
        request.publishedIn(),
        snapshot.complete());
    return preview(fund, calculationVersions.unchanged(request), request.validFrom());
  }

  public KidFigure publishGiven(OcfGivenPublicationRequest request) {
    request.refuseUnlessThePeriodFitsTheBasis();
    var alreadyRecorded = kidAlreadyRecorded(request.validFrom(), request.publishedIn());
    if (publicationRepository.isRecorded(
        request.fund().getCode(), request.validFrom(), request.publishedIn())) {
      throw request.refused(alreadyRecorded);
    }
    var publication = OcfPublication.given(request);
    var recorded =
        unlessRecordedMeanwhile(
            () -> publicationRepository.recordGiven(publication),
            recordedConcurrently -> request.refused(alreadyRecorded, recordedConcurrently));
    log.info(
        "OCF given figure recorded: fund={}, validFrom={}, publishedOcf={}, basis={},"
            + " publishedIn={}",
        request.fund().getCode(),
        request.validFrom(),
        request.publishedOcf(),
        request.basis(),
        request.publishedIn());
    return recorded;
  }

  private OcfPublicationPreview preview(
      TulevaFund fund, OcfSnapshot snapshot, LocalDate validFrom) {
    var fee = managementFeeOn(fund, validFrom);
    var publishedOcf = publishedOcf(snapshot, fee);
    var precision = PublishedPrecision.of(fund);
    return new OcfPublicationPreview(
        fund.getCode(),
        YearMonth.from(snapshot.snapshotMonth()),
        snapshot.version(),
        snapshot.complete(),
        snapshot.checks(),
        OcfCalculationFingerprint.of(snapshot),
        snapshot.totalOcf(),
        snapshot.managementFeeRate(),
        validFrom,
        fee.annualRate(),
        publishedOcf,
        precision.percent(publishedOcf),
        precision.decimals(),
        publicationRepository.findInForceOn(fund.getCode(), validFrom.minusDays(1)).orElse(null),
        exceptionRepository.findForTheCurrentCalculation(snapshot).orElse(null),
        snapshot.publishedAt() != null);
  }

  private void refuseUnlessTheIncompletenessWasExcepted(
      OcfSnapshot snapshot, OcfPublicationRequest request) {
    if (snapshot.complete()) {
      return;
    }
    var exception = exceptionRepository.findForTheCurrentCalculation(snapshot);
    if (exception.isEmpty()) {
      throw request.refusal(
          NOT_PUBLISHED
              + "the calculation is incomplete (%s) and the fund manager has not decided an"
                  .formatted(snapshot.checks())
              + " exception for it");
    }
    if (samePerson(request.publishedBy(), exception.get().decidedBy())) {
      throw request.refusal(
          NOT_PUBLISHED + "the publisher must be someone other than who decided the exception");
    }
  }

  private static <T> T unlessRecordedMeanwhile(
      Supplier<T> recording,
      Function<DuplicateKeyException, OcfPublicationRefusedException> refusal) {
    try {
      return recording.get();
    } catch (DuplicateKeyException recordedConcurrently) {
      throw refusal.apply(recordedConcurrently);
    }
  }

  private static String kidAlreadyRecorded(LocalDate validFrom, String publishedIn) {
    return "a KID valid from %s is already recorded in %s".formatted(validFrom, publishedIn);
  }

  private static boolean samePerson(String one, String other) {
    return one.strip().equalsIgnoreCase(other.strip());
  }

  private static void refuseUnlessAfterTheMonth(
      TulevaFund fund, YearMonth month, LocalDate validFrom) {
    if (!validFrom.isAfter(month.atEndOfMonth())) {
      throw new OcfPublicationRefusedException(
          "A KID figure takes effect only after the month it was calculated for: fund=%s,"
                  .formatted(fund.getCode())
              + " month=%s, validFrom=%s".formatted(month, validFrom));
    }
  }

  private static void refuseUnlessPublishable(
      OcfSnapshot snapshot, OcfPublicationRequest request, BigDecimal publishedOcf) {
    if (snapshot.publishedAt() != null) {
      throw request.refusal(NOT_PUBLISHED + "the calculation is already published");
    }
    if (publishedOcf.compareTo(request.publishedOcf()) != 0) {
      throw request.refusal(
          NOT_PUBLISHED
              + "the figure is %s, not the %s that was previewed"
                  .formatted(publishedOcf.toPlainString(), request.publishedOcf().toPlainString()));
    }
  }

  private FeeRate managementFeeOn(TulevaFund fund, LocalDate validFrom) {
    return feeRateRepository
        .findValidRate(fund, MANAGEMENT, validFrom)
        .orElseThrow(
            () ->
                new OcfPublicationRefusedException(
                    "No management fee rate on the publication date: fund=%s, validFrom=%s"
                        .formatted(fund.getCode(), validFrom)));
  }

  private static BigDecimal publishedOcf(OcfSnapshot snapshot, FeeRate fee) {
    return snapshot
        .totalOcf()
        .subtract(snapshot.managementFeeRate())
        .add(fee.annualRate())
        .setScale(OcfCalculationService.SCALE, HALF_UP);
  }
}
