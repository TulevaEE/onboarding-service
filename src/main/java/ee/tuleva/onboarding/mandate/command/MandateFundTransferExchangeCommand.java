package ee.tuleva.onboarding.mandate.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class MandateFundTransferExchangeCommand {

  @NotNull private String sourceFundIsin;

  @NotNull
  @Min(0)
  @Max(1)
  private BigDecimal amount;

  @NotNull private String targetFundIsin;
}
