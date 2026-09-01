package ee.tuleva.onboarding.mandate.command;

import static java.util.Objects.requireNonNull;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@ToString
public class MandateFundTransferExchangeCommand {

  @NotNull private @Nullable String sourceFundIsin;

  @NotNull
  @Min(0)
  @Max(1)
  private @Nullable BigDecimal amount;

  @NotNull private @Nullable String targetFundIsin;

  public String getSourceFundIsin() {
    return requireNonNull(sourceFundIsin, "sourceFundIsin must be validated first");
  }

  public BigDecimal getAmount() {
    return requireNonNull(amount, "amount must be validated first");
  }

  public String getTargetFundIsin() {
    return requireNonNull(targetFundIsin, "targetFundIsin must be validated first");
  }
}
