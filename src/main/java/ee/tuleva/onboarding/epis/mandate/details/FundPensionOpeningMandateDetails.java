package ee.tuleva.onboarding.epis.mandate.details;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.mandate.MandateType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class FundPensionOpeningMandateDetails extends MandateDetails {
  @NotNull private final Pillar pillar;
  @NotNull private final FundPensionDuration duration;
  @NotNull private final BankAccountDetails bankAccountDetails;

  @JsonCreator
  public FundPensionOpeningMandateDetails(
      @JsonProperty("pillar") Pillar pillar,
      @JsonProperty("duration") FundPensionDuration duration,
      @JsonProperty("bankAccountDetails") BankAccountDetails bankAccountDetails) {
    super(MandateType.FUND_PENSION_OPENING);
    this.pillar = pillar;
    this.duration = duration;
    this.bankAccountDetails = bankAccountDetails;
  }

  public record FundPensionDuration(int durationYears, boolean recommendedDuration) {}
}
