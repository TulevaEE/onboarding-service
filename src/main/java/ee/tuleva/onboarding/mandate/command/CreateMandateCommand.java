package ee.tuleva.onboarding.mandate.command;

import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
public class CreateMandateCommand {

    private String futureContributionFundIsin;

    @Valid
    @NotNull
    private List<MandateFundTransferExchangeCommand> fundTransferExchanges;

}
