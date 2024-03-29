package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.user.address.Address;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class CreateMandateCommand {

  private String futureContributionFundIsin;

  @Valid @NotNull private List<MandateFundTransferExchangeCommand> fundTransferExchanges;

  @Valid @NotNull private Address address;
}
