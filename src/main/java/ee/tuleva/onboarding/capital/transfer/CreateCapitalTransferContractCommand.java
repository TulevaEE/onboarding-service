package ee.tuleva.onboarding.capital.transfer;

import ee.tuleva.onboarding.capital.transfer.iban.ValidIban;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
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

  @NotNull
  @Min(0)
  private BigDecimal totalPrice;

  @NotNull
  @Min(0)
  private BigDecimal unitCount;

  @NotNull
  @Min(0)
  private BigDecimal unitsOfMemberBonus;
}
