package ee.tuleva.onboarding.epis.mandate.details;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.mandate.MandateType;
import lombok.Getter;

@Getter
public class FundPensionOpeningMandateDetails extends MandateDetails {

  private final Pillar pillar;
  private final FundPensionFrequency frequency;
  private final FundPensionDuration duration;
  private final BankAccountDetails bankAccountDetails;

  @JsonCreator
  public FundPensionOpeningMandateDetails(
      @JsonProperty("pillar") Pillar pillar,
      @JsonProperty("frequency") FundPensionFrequency frequency,
      @JsonProperty("duration") FundPensionDuration duration,
      @JsonProperty("bankAccountDetails") BankAccountDetails bankAccountDetails) {
    super(MandateType.FUND_PENSION_OPENING);
    this.pillar = pillar;
    this.frequency = frequency;
    this.duration = duration;
    this.bankAccountDetails = bankAccountDetails;
  }

  public enum FundPensionFrequency {
    MONTHLY,
    EVERY_3_MONTHS,
    EVERY_12_MONTHS,
  }

  public record FundPensionDuration(int durationYears, boolean recommendedDuration) {}
}
