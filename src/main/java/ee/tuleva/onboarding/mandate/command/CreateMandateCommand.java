package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.user.address.Address;
import java.math.BigDecimal;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@ToString
public class CreateMandateCommand {

  private String futureContributionFundIsin;

  @Valid @NotNull private List<MandateFundTransferExchangeCommand> fundTransferExchanges;

  @Valid @NotNull private Address address;

  @Valid @Nullable private BigDecimal paymentRate;
}
