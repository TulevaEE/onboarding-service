package ee.tuleva.onboarding.capital.transfer;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActiveTransferCapital {

  private final CapitalTransferContractRepository contractRepository;

  public Map<MemberCapitalEventType, BigDecimal> beingSoldBy(Member seller) {
    return activeTransferSums(contractRepository.findAllBySellerId(seller.getId()));
  }

  public Map<MemberCapitalEventType, BigDecimal> beingBoughtBy(Member buyer) {
    return activeTransferSums(contractRepository.findAllByBuyerId(buyer.getId()));
  }

  private Map<MemberCapitalEventType, BigDecimal> activeTransferSums(
      List<CapitalTransferContract> userTransfers) {
    return userTransfers.stream()
        .filter(contract -> contract.getState().isInProgress())
        .flatMap(contract -> contract.getTransferAmounts().stream())
        .collect(
            Collectors.toMap(
                CapitalTransferAmount::type, CapitalTransferAmount::bookValue, BigDecimal::add));
  }
}
