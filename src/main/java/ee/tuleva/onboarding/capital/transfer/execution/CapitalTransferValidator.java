package ee.tuleva.onboarding.capital.transfer.execution;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CapitalTransferValidator {

  private final MemberCapitalEventRepository memberCapitalEventRepository;

  public void validateContract(CapitalTransferContract contract) {
    if (contract == null) {
      throw new IllegalArgumentException("Capital transfer contract not found");
    }

    if (contract.getState() != CapitalTransferContractState.APPROVED) {
      throw new IllegalStateException(
          String.format(
              "Capital transfer contract %d is not in APPROVED state (current state: %s)",
              contract.getId(), contract.getState()));
    }
  }

  public void validateSufficientCapital(CapitalTransferContract contract) {
    Long sellerId = contract.getSeller().getId();
    List<MemberCapitalEvent> sellerEvents =
        memberCapitalEventRepository.findAllByMemberId(sellerId);

    Map<MemberCapitalEventType, BigDecimal> availableCapitalByType =
        calculateAvailableCapital(sellerEvents);

    for (CapitalTransferAmount transferAmount : contract.getTransferAmounts()) {
      if (shouldSkipTransfer(transferAmount)) {
        continue;
      }

      BigDecimal availableCapital =
          availableCapitalByType.getOrDefault(transferAmount.type(), BigDecimal.ZERO);

      if (availableCapital.compareTo(transferAmount.bookValue()) < 0) {
        throw new IllegalStateException(
            String.format(
                "Seller has insufficient %s capital. Available: %s, Required: %s",
                transferAmount.type(), availableCapital, transferAmount.bookValue()));
      }
    }
  }

  public boolean shouldSkipTransfer(CapitalTransferAmount transferAmount) {
    return transferAmount.bookValue() == null
        || transferAmount.bookValue().compareTo(BigDecimal.ZERO) == 0;
  }

  private Map<MemberCapitalEventType, BigDecimal> calculateAvailableCapital(
      List<MemberCapitalEvent> events) {
    return events.stream()
        .collect(
            Collectors.groupingBy(
                MemberCapitalEvent::getType,
                Collectors.mapping(
                    MemberCapitalEvent::getFiatValue,
                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
  }
}
