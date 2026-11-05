package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.MONTHLY_ESTIMATE;

import ee.tuleva.onboarding.investment.fees.FeeRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

record OcfPublication(
    String fundCode,
    LocalDate validFrom,
    BigDecimal publishedOcf,
    int publishedDecimals,
    OcfPublicationBasis basis,
    String basisReason,
    @Nullable Long snapshotId,
    @Nullable BigDecimal managementFeeRate,
    @Nullable Long managementFeeRateId,
    @Nullable LocalDate exPostPeriodStart,
    @Nullable LocalDate exPostPeriodEnd,
    String publishedIn,
    String publishedBy) {

  static OcfPublication notFromACalculation(
      String fundCode,
      LocalDate validFrom,
      BigDecimal publishedOcf,
      int publishedDecimals,
      OcfPublicationBasis basis,
      String basisReason,
      @Nullable LocalDate exPostPeriodStart,
      @Nullable LocalDate exPostPeriodEnd,
      String publishedIn,
      String publishedBy) {
    return new OcfPublication(
        fundCode,
        validFrom,
        publishedOcf,
        publishedDecimals,
        basis,
        basisReason,
        null,
        null,
        null,
        exPostPeriodStart,
        exPostPeriodEnd,
        publishedIn,
        publishedBy);
  }

  static OcfPublication fromCalculation(
      OcfPublicationRequest request, OcfSnapshot snapshot, BigDecimal publishedOcf, FeeRate fee) {
    return new OcfPublication(
        request.fund().getCode(),
        request.validFrom(),
        publishedOcf,
        PublishedPrecision.of(request.fund()).decimals(),
        MONTHLY_ESTIMATE,
        request.basisReason(),
        snapshot.storedId(),
        fee.annualRate(),
        fee.id(),
        null,
        null,
        request.publishedIn(),
        request.publishedBy());
  }

  static OcfPublication given(OcfGivenPublicationRequest request) {
    return notFromACalculation(
        request.fund().getCode(),
        request.validFrom(),
        request.publishedOcf(),
        request.publishedDecimals(),
        request.basis(),
        request.basisReason(),
        request.exPostPeriodStart(),
        request.exPostPeriodEnd(),
        request.publishedIn(),
        request.publishedBy());
  }

  KidFigure recordedAs(long publicationId) {
    return new KidFigure(
        publicationId, validFrom, publishedOcf, publishedDecimals, basis, publishedIn);
  }
}
