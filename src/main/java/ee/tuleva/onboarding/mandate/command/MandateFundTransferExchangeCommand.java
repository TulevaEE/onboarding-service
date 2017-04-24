package ee.tuleva.onboarding.mandate.command;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@Setter
@ToString
public class MandateFundTransferExchangeCommand {

    @NotNull
    private String sourceFundIsin;
    @NotNull
    @Min(0)
    @Max(1)
    private BigDecimal amount;
    @NotNull
    private String targetFundIsin;

}