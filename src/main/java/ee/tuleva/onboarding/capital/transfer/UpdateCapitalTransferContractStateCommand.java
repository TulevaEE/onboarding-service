package ee.tuleva.onboarding.capital.transfer;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateCapitalTransferContractStateCommand {
  @NotNull private CapitalTransferContractState state;
}
