package ee.tuleva.onboarding.mandate.details;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.mandate.ApplicationType;
import ee.tuleva.onboarding.mandate.MandateType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

// TODO currently unused – needs to be migrated together with transfer mandate via mandate batch
@Getter
public class SelectionMandateDetails extends MandateDetails {

  @NotNull private final String futureContributionFundIsin;

  @JsonCreator
  public SelectionMandateDetails(
      @JsonProperty("futureContributionFundIsin") String futureContributionFundIsin) {
    super(MandateType.SELECTION);
    this.futureContributionFundIsin = futureContributionFundIsin;
  }

  @Override
  public ApplicationType getApplicationType() {
    return ApplicationType.SELECTION;
  }
}
