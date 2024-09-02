package ee.tuleva.onboarding.epis.mandate.details;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.mandate.MandateType;
import lombok.Getter;

@Getter
public class FundPensionOpeningMandateDetails extends MandateDetails {

  private final Integer pillar;
  private final FundPensionFrequency frequency;
  private final FundPensionDuration duration;
  private final BankAccountDetails bankAccountDetails;

  public FundPensionOpeningMandateDetails(
      int pillar,
      FundPensionFrequency frequency,
      FundPensionDuration duration,
      BankAccountDetails bankAccountDetails) {
    super(MandateType.FUND_PENSION_OPENING);
    this.pillar = pillar;
    this.frequency = frequency;
    this.duration = duration;
    this.bankAccountDetails = bankAccountDetails;
  }

  @JsonIgnore
  public boolean isSecondPillar() {
    return pillar == 2;
  }

  @JsonIgnore
  public boolean isThirdPillar() {
    return pillar == 3;
  }

  public enum FundPensionFrequency {
    MONTHLY,
    EVERY_3_MONTHS,
    EVERY_12_MONTHS,
  }

  public record FundPensionDuration(int durationYears, boolean recommendedDuration) {}
}
