package ee.tuleva.onboarding.mandate.command;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@ToString
public class CreateMandateCommand {

    private String futureContributionFundIsin;

    private Integer pillar;

    @Valid
    @NotNull
    private List<MandateFundTransferExchangeCommand> fundTransferExchanges;

}
