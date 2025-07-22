package ee.tuleva.onboarding.capital.transfer;

import ee.tuleva.onboarding.member.MemberLookupResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CapitalTransferContractDto {
  Long id;
  MemberLookupResponse seller;
  MemberLookupResponse buyer;
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
        .seller(MemberLookupResponse.from(contract.getSeller().getUser().getMemberOrThrow()))
        .buyer(MemberLookupResponse.from(contract.getBuyer().getUser().getMemberOrThrow()))
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
