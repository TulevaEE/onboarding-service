package ee.tuleva.onboarding.mandate;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class MandateFundTransferExchangeCommand {

    @NotNull
    private String sourceFundIsin;
    @NotNull
    @Min(0)
    @Max(100)
    private Integer percent;
    @NotNull
    private String targetFundIsin;

}