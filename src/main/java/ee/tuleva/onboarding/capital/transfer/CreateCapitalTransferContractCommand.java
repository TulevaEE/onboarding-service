package ee.tuleva.onboarding.capital.transfer;

import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.iban.ValidIban;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateCapitalTransferContractCommand {
  @NotNull private Long buyerMemberId;

  @NotNull @ValidIban private String iban;

  @NotNull private List<CapitalTransferAmount> transferAmounts;
}
