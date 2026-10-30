package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcfPublicationService {

  private static final int PUBLISHED_PERCENT_DECIMAL_PLACES = 2;

  private final OcfSnapshotRepository ocfSnapshotRepository;
  private final FeeRateRepository feeRateRepository;

  public OcfPublicationPreview preview(TulevaFund fund, YearMonth month, LocalDate validFrom) {
    refuseUnlessAfterTheMonth(fund, month, validFrom);
    var snapshot =
        ocfSnapshotRepository
            .findByFundAndMonth(fund.getCode(), month.atDay(1))
            .orElseThrow(
                () ->
                    new OcfPublicationRefusedException(
                        "No OCF calculation for the month: fund=%s, month=%s"
                            .formatted(fund.getCode(), month)));
    return preview(fund, snapshot, validFrom);
  }

  public OcfPublicationPreview publish(OcfPublicationRequest request) {
    var fund = request.fund();
    refuseUnlessAfterTheMonth(fund, request.month(), request.validFrom());
    var snapshot = theRequestedVersion(request);
    var fee = managementFeeOn(fund, request.validFrom());
    var publishedOcf = publishedOcf(snapshot, fee);
    refuseUnlessPublishable(snapshot, request, publishedOcf);
    var publication =
        new OcfPublication(
            request.validFrom(),
            publishedOcf,
            fee.annualRate(),
            fee.id(),
            request.publishedIn(),
            request.publishedBy(),
            request.approvedBy(),
            request.exceptionReason());
    if (!ocfSnapshotRepository.recordPublication(snapshot, publication)) {
      throw new OcfPublicationRefusedException(
          "The OCF calculation changed or was published since it was read: fund=%s, month=%s,"
                  .formatted(fund.getCode(), request.month())
              + " version=%d".formatted(request.version()));
    }
    log.info(
        "OCF published: fund={}, month={}, version={}, validFrom={}, publishedOcf={},"
            + " publishedIn={}, exception={}",
        fund.getCode(),
        request.month(),
        request.version(),
        request.validFrom(),
        publishedOcf,
        request.publishedIn(),
        request.exceptionReason() != null);
    return preview(fund, theRequestedVersion(request), request.validFrom());
  }

  private OcfSnapshot theRequestedVersion(OcfPublicationRequest request) {
    var fund = request.fund();
    return ocfSnapshotRepository
        .findVersion(fund.getCode(), request.month().atDay(1), request.version())
        .orElseThrow(
            () ->
                new OcfPublicationRefusedException(
                    "No such OCF calculation: fund=%s, month=%s, version=%d"
                        .formatted(fund.getCode(), request.month(), request.version())));
  }

  private OcfPublicationPreview preview(
      TulevaFund fund, OcfSnapshot snapshot, LocalDate validFrom) {
    var fee = managementFeeOn(fund, validFrom);
    var publishedOcf = publishedOcf(snapshot, fee);
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
        publishedOcf.movePointRight(2).setScale(PUBLISHED_PERCENT_DECIMAL_PLACES, HALF_UP),
        ocfSnapshotRepository
            .findPublishedInForceOn(fund.getCode(), validFrom.minusDays(1))
            .orElse(null),
        snapshot.publishedAt() != null);
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
      throw refused(request, "the calculation is already published");
    }
    if (!OcfCalculationFingerprint.of(snapshot).equals(request.calculationFingerprint())) {
      throw refused(request, "the calculation was recalculated since it was previewed");
    }
    if (publishedOcf.compareTo(request.publishedOcf()) != 0) {
      throw refused(
          request,
          "the figure is %s, not the %s that was previewed"
              .formatted(publishedOcf.toPlainString(), request.publishedOcf().toPlainString()));
    }
    refuseUnlessTheExceptionFitsTheCompleteness(snapshot, request);
    refuseAnApproverWhoIsThePublisher(request);
  }

  private static void refuseUnlessTheExceptionFitsTheCompleteness(
      OcfSnapshot snapshot, OcfPublicationRequest request) {
    var anException = !isBlank(request.exceptionReason()) && !isBlank(request.approvedBy());
    if (snapshot.complete() && request.exceptionReason() != null) {
      throw refused(request, "a complete calculation is published without an exception");
    }
    if (!snapshot.complete() && !anException) {
      throw refused(
          request,
          "the calculation is incomplete (%s), so publishing it needs an exception reason and a"
                  .formatted(snapshot.checks())
              + " second approver");
    }
  }

  private static void refuseAnApproverWhoIsThePublisher(OcfPublicationRequest request) {
    var approvedBy = request.approvedBy();
    if (approvedBy != null && request.publishedBy().strip().equalsIgnoreCase(approvedBy.strip())) {
      throw refused(request, "the second approver must be someone other than the publisher");
    }
  }

  private static OcfPublicationRefusedException refused(OcfPublicationRequest request, String why) {
    return new OcfPublicationRefusedException(
        "OCF not published, %s: fund=%s, month=%s, version=%d"
            .formatted(why, request.fund().getCode(), request.month(), request.version()));
  }

  private static boolean isBlank(@Nullable String text) {
    return text == null || text.isBlank();
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
