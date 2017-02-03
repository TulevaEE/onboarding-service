package ee.tuleva.onboarding.mandate;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
public class CreateMandateCommand {

    private String futureContributionFundIsin;

    @NotNull
    private List<MandateFundTransferExchangeCommand> fundTransferExchanges;

}
