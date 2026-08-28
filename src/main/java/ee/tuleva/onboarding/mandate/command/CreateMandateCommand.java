package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.country.Country;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class CreateMandateCommand {

  private String futureContributionFundIsin;

  @Valid @NotNull private List<MandateFundTransferExchangeCommand> fundTransferExchanges;

  @Valid @NotNull private Country address;

  @AssertTrue(
      message = "either futureContributionFundIsin or fundTransferExchanges must be present")
  private boolean isSourceIsinPresent() {
    return (futureContributionFundIsin != null && !futureContributionFundIsin.isBlank())
        || (fundTransferExchanges != null && !fundTransferExchanges.isEmpty());
  }
}
