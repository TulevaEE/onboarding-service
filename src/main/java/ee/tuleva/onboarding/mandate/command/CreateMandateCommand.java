package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.country.Country;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@ToString
public class CreateMandateCommand {

  private @Nullable String futureContributionFundIsin;

  @Valid @NotNull private @Nullable List<MandateFundTransferExchangeCommand> fundTransferExchanges;

  @Valid @NotNull private @Nullable Country address;

  @AssertTrue(
      message = "either futureContributionFundIsin or fundTransferExchanges must be present")
  private boolean isSourceIsinPresent() {
    return (futureContributionFundIsin != null && !futureContributionFundIsin.isBlank())
        || (fundTransferExchanges != null && !fundTransferExchanges.isEmpty());
  }
}
