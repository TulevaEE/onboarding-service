package ee.tuleva.onboarding.capital.transfer.execution;

import ee.tuleva.onboarding.capital.CapitalCalculations;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CapitalTransferValidator {

  private static final Set<MemberCapitalEventType> SELLABLE_CAPITAL_TYPES =
      Set.of(
          MemberCapitalEventType.CAPITAL_PAYMENT,
          MemberCapitalEventType.MEMBERSHIP_BONUS,
          MemberCapitalEventType.WORK_COMPENSATION,
          MemberCapitalEventType.CAPITAL_ACQUIRED);

  private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.01");

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

    for (CapitalTransferAmount transferAmount : contract.getTransferAmounts()) {
      if (shouldSkipTransfer(transferAmount)) {
        continue;
      }

      BigDecimal availableCapital =
          calculateAvailableCapitalForType(
              sellerEvents, transferAmount.type(), transferAmount.ownershipUnitPrice());

      BigDecimal difference = availableCapital.subtract(transferAmount.bookValue());

      if (difference.compareTo(BigDecimal.ZERO) < 0
          && difference.compareTo(ROUNDING_TOLERANCE.negate()) >= 0) {
        log.info(
            "Capital validation passed with rounding tolerance for type {}. "
                + "Available: {}, Required: {}, Difference: {}",
            transferAmount.type(),
            availableCapital,
            transferAmount.bookValue(),
            difference);
      }

      if (difference.compareTo(ROUNDING_TOLERANCE.negate()) < 0) {
        throw new IllegalStateException(
            String.format(
                "Seller has insufficient %s capital. Available: %s, Required: %s (difference: %s)",
                transferAmount.type(), availableCapital, transferAmount.bookValue(), difference));
      }
    }
  }

  public boolean shouldSkipTransfer(CapitalTransferAmount transferAmount) {
    return transferAmount.bookValue() == null
        || transferAmount.bookValue().compareTo(BigDecimal.ZERO) == 0;
  }

  private BigDecimal calculateAvailableCapitalForType(
      List<MemberCapitalEvent> events, MemberCapitalEventType type, BigDecimal ownershipUnitPrice) {

    return events.stream()
        .filter(event -> event.getType() == type)
        .filter(event -> SELLABLE_CAPITAL_TYPES.contains(event.getType()))
        .map(
            event ->
                CapitalCalculations.calculateCapitalValue(
                    event.getOwnershipUnitAmount(), ownershipUnitPrice))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
