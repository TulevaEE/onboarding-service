package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;

import ee.tuleva.onboarding.capital.CapitalRow;
import ee.tuleva.onboarding.capital.CapitalService;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CapitalTransferCreationValidator {

  private final CapitalService capitalService;
  private final ActiveTransferCapital activeTransferCapital;

  void validate(Member seller, Member buyer, CreateCapitalTransferContractCommand command) {
    log.info(
        "Validating command {} for seller {} and buyer {}", command, seller.getId(), buyer.getId());

    if (isAmountsEmpty(command)) {
      throw new IllegalArgumentException("No amounts specified");
    }

    if (!hasPositiveNonZeroAmountsPrices(command)) {
      throw new IllegalArgumentException("Amounts or prices have negative or zero values");
    }

    if (!hasOnlyOneOfType(command)) {
      throw new IllegalArgumentException("Duplicate types specified");
    }

    if (!hasOnlyLiquidatableTypes(command)) {
      throw new IllegalArgumentException("Non-liquidatable capital types included in command");
    }

    if (seller.getId().equals(buyer.getId())) {
      throw new IllegalArgumentException("Seller and buyer cannot be the same person.");
    }

    if (!hasEnoughMemberCapital(seller, command)) {
      throw new IllegalStateException("Seller does not have enough member capital");
    }

    if (!isTransferWithinConcentrationLimit(buyer, command)) {
      throw new IllegalStateException("Buyer would exceed concentration limit after transfer");
    }
  }

  private boolean isTransferWithinConcentrationLimit(
      Member buyer, CreateCapitalTransferContractCommand command) {
    var user = buyer.getUser();

    var totalMemberCapital =
        capitalService.getCapitalRows(user.getMemberId()).stream()
            .filter(event -> event.type() != UNVESTED_WORK_COMPENSATION)
            .map(CapitalRow::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var memberCapitalBookValueBeingAcquired =
        getCapitalBeingAcquiredInOtherTransfers(buyer).values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var memberCapitalBookValueToBeAcquired =
        command.getTransferAmounts().stream()
            .map(CapitalTransferAmount::bookValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var buyerMemberCapitalAfterPurchases =
        totalMemberCapital
            .add(memberCapitalBookValueBeingAcquired)
            .add(memberCapitalBookValueToBeAcquired);

    var concentrationLimit = capitalService.getCapitalConcentrationUnitLimit();
    return concentrationLimit.compareTo(buyerMemberCapitalAfterPurchases) > 0;
  }

  Map<MemberCapitalEventType, BigDecimal> getCapitalBeingAcquiredInOtherTransfers(Member buyer) {
    return activeTransferCapital.beingBoughtBy(buyer);
  }

  Map<MemberCapitalEventType, BigDecimal> getCapitalBeingSoldInOtherTransfers(Member seller) {
    return activeTransferCapital.beingSoldBy(seller);
  }

  private boolean hasEnoughMemberCapital(
      Member seller, CreateCapitalTransferContractCommand command) {
    var capitalSoldInOtherTransfers = getCapitalBeingSoldInOtherTransfers(seller);

    for (CapitalTransferAmount transferAmount : command.getTransferAmounts()) {
      var totalSellerMemberCapitalOfType = totalCapitalOfType(seller, transferAmount.type());

      var capitalOfTypeBeingSoldInOtherTransfers =
          capitalSoldInOtherTransfers.getOrDefault(transferAmount.type(), BigDecimal.ZERO);
      var totalCapitalToBeSold =
          transferAmount.bookValue().add(capitalOfTypeBeingSoldInOtherTransfers);

      if (totalSellerMemberCapitalOfType.compareTo(totalCapitalToBeSold) < 0) {
        return false;
      }
    }
    return true;
  }

  private BigDecimal totalCapitalOfType(Member seller, MemberCapitalEventType type) {
    return capitalService.getCapitalRows(seller.getId()).stream()
        .filter(event -> event.type() == type)
        .map(CapitalRow::getValue)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private boolean isAmountsEmpty(CreateCapitalTransferContractCommand command) {
    return command.getTransferAmounts().isEmpty();
  }

  private boolean hasOnlyOneOfType(CreateCapitalTransferContractCommand command) {
    return command.getTransferAmounts().stream()
            .map(CapitalTransferAmount::type)
            .collect(Collectors.toSet())
            .size()
        == command.getTransferAmounts().size();
  }

  private boolean hasPositiveNonZeroAmountsPrices(CreateCapitalTransferContractCommand command) {
    return command.getTransferAmounts().stream()
        .allMatch(
            amount ->
                amount.bookValue().compareTo(BigDecimal.ZERO) > 0
                    && amount.price().compareTo(BigDecimal.ZERO) > 0);
  }

  private boolean hasOnlyLiquidatableTypes(CreateCapitalTransferContractCommand command) {
    var typesToLiquidate =
        command.getTransferAmounts().stream()
            .map(CapitalTransferAmount::type)
            .collect(Collectors.toSet());
    var liquidatableTypes =
        Set.of(CAPITAL_PAYMENT, WORK_COMPENSATION, MEMBERSHIP_BONUS, CAPITAL_ACQUIRED);

    return liquidatableTypes.containsAll(typesToLiquidate);
  }
}
