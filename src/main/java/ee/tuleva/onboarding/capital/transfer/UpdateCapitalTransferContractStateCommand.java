package ee.tuleva.onboarding.capital.transfer;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@NoArgsConstructor
public class UpdateCapitalTransferContractStateCommand {
  @NotNull private @Nullable CapitalTransferContractState state;
}
