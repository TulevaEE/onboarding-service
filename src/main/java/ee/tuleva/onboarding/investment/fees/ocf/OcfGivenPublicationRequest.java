package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.EX_POST;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record OcfGivenPublicationRequest(
    @NotNull TulevaFund fund,
    @NotNull LocalDate validFrom,
    @NotNull @Positive @DecimalMax("0.1") @Digits(integer = 1, fraction = 8)
        BigDecimal publishedOcf,
    @NotNull @Min(1) @Max(4) Integer publishedDecimals,
    @NotNull OcfPublicationBasis basis,
    @Nullable LocalDate exPostPeriodStart,
    @Nullable LocalDate exPostPeriodEnd,
    @NotBlank @Size(max = 200) String publishedIn,
    @NotBlank @Size(max = 200) String publishedBy,
    @NotBlank @Size(max = 1000) String basisReason) {

  void refuseUnlessThePeriodFitsTheBasis() {
    if (basis != EX_POST) {
      refuseAnEstimateNamingAPeriod();
      return;
    }
    var start = exPostPeriodStart;
    var end = exPostPeriodEnd;
    if (start == null || end == null) {
      throw refused("an ex-post figure must name the start and the end of its period");
    }
    refuseAPeriodEndingBeforeItStarts(start, end);
    refuseAPeriodNotEndingBeforeTheKidTakesEffect(end);
  }

  private void refuseAnEstimateNamingAPeriod() {
    if (exPostPeriodStart != null || exPostPeriodEnd != null) {
      throw refused("an estimate must not name an ex-post period");
    }
  }

  private void refuseAPeriodEndingBeforeItStarts(LocalDate start, LocalDate end) {
    if (start.isAfter(end)) {
      throw refused("an ex-post period must not end before it starts");
    }
  }

  private void refuseAPeriodNotEndingBeforeTheKidTakesEffect(LocalDate end) {
    if (!end.isBefore(validFrom)) {
      throw refused("an ex-post period must end before the KID takes effect");
    }
  }

  OcfPublicationRefusedException refused(String why) {
    return new OcfPublicationRefusedException(described(why));
  }

  OcfPublicationRefusedException refused(String why, Throwable cause) {
    return new OcfPublicationRefusedException(described(why), cause);
  }

  private String described(String why) {
    return "OCF not recorded, %s: fund=%s, validFrom=%s, basis=%s"
        .formatted(why, fund.getCode(), validFrom, basis);
  }
}
