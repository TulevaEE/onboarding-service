package ee.tuleva.onboarding.mandate.command;

import ee.tuleva.onboarding.user.address.Address;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@ToString
public class CreateMandateCommand {

    private String futureContributionFundIsin;

    @Valid
    @NotNull
    private List<MandateFundTransferExchangeCommand> fundTransferExchanges;

    @Valid
    @Nullable
    private Address address;

}
