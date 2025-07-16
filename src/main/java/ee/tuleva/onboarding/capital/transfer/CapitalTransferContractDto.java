package ee.tuleva.onboarding.capital.transfer;

import ee.tuleva.onboarding.user.response.UserResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CapitalTransferContractDto {
  Long id;
  UserResponse seller;
  UserResponse buyer;
  String iban;
  BigDecimal unitPrice;
  BigDecimal unitCount;
  BigDecimal unitsOfMemberBonus;
  CapitalTransferContractState state;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;

  public static CapitalTransferContractDto from(CapitalTransferContract contract) {
    return CapitalTransferContractDto.builder()
        .id(contract.getId())
        .seller(UserResponse.from(contract.getSeller().getUser()))
        .buyer(UserResponse.from(contract.getBuyer().getUser()))
        .iban(contract.getIban())
        .unitPrice(contract.getUnitPrice())
        .unitCount(contract.getUnitCount())
        .unitsOfMemberBonus(contract.getUnitsOfMemberBonus())
        .state(contract.getState())
        .createdAt(contract.getCreatedAt())
        .updatedAt(contract.getUpdatedAt())
        .build();
  }
}
