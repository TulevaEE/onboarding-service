package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.country.Country;
import jakarta.validation.Valid;
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
}
