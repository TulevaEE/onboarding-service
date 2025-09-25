package ee.tuleva.onboarding.capital.transfer.execution;

import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.APPROVED_AND_NOTIFIED;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.CAPITAL_TRANSFER_APPROVED_BY_BOARD;

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent;
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractRepository;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CapitalTransferExecutor {

  private final CapitalTransferContractRepository contractRepository;
  private final MemberCapitalEventRepository memberCapitalEventRepository;
  private final AggregatedCapitalEventRepository aggregatedCapitalEventRepository;
  private final CapitalTransferValidator validator;
  private final CapitalTransferContractService contractService;
  private final CapitalTransferEventLinkRepository linkRepository;

  @Transactional
  public void execute(CapitalTransferContract contract) {
    log.info("Starting capital transfer execution for contract {}", contract.getId());

    validator.validateContract(contract);
    validator.validateSufficientCapital(contract);

    BigDecimal currentUnitPrice = getCurrentOwnershipUnitPrice();
    LocalDate accountingDate = LocalDate.now(ZoneId.of("Europe/Tallinn"));

    var sellerAvailableCapital = validator.calculateAvailableCapitalForSeller(contract);

    for (CapitalTransferAmount transferAmount : contract.getTransferAmounts()) {
      if (validator.shouldSkipTransfer(transferAmount)) {
        log.debug("Skipping transfer with zero/null book value for type {}", transferAmount.type());
        continue;
      }

      executeTransferAmount(
          contract, sellerAvailableCapital, transferAmount, currentUnitPrice, accountingDate);
    }

    // TODO use updateStateBySystem here
    contract.executed();
    contractRepository.save(contract);

    log.info(
        "Capital transfer {} executed successfully. Transferred capital from member {} to member {}",
        contract.getId(),
        contract.getSeller().getId(),
        contract.getBuyer().getId());

    sendApprovedByBoardEmails(contract);
  }

  private void sendApprovedByBoardEmails(CapitalTransferContract transfer) {
    try {
      contractService.sendContractEmail(
          transfer.getBuyer().getUser(), CAPITAL_TRANSFER_APPROVED_BY_BOARD, transfer);
    } catch (Exception e) {
      log.error("Failed to send approved email to buyer for contract {}", transfer.getId(), e);
    }

    try {
      contractService.sendContractEmail(
          transfer.getSeller().getUser(), CAPITAL_TRANSFER_APPROVED_BY_BOARD, transfer);
    } catch (Exception e) {
      log.error("Failed to send approved email to seller for contract {}", transfer.getId(), e);
    }

    contractService.updateStateBySystem(transfer.getId(), APPROVED_AND_NOTIFIED);
  }

  private void executeTransferAmount(
      CapitalTransferContract contract,
      Map<MemberCapitalEventType, BigDecimal> sellerAvailableAmounts,
      CapitalTransferAmount transferAmount,
      BigDecimal currentUnitPrice,
      LocalDate accountingDate) {

    BigDecimal totalUnitsToTransfer =
        calculateUnitsToTransfer(transferAmount, currentUnitPrice, sellerAvailableAmounts);

    createSellerWithdrawalEvent(contract, transferAmount, totalUnitsToTransfer, accountingDate);
    createBuyerAcquisitionEvent(contract, transferAmount, totalUnitsToTransfer, accountingDate);
  }

  private void createSellerWithdrawalEvent(
      CapitalTransferContract contract,
      CapitalTransferAmount transferAmount,
      BigDecimal totalUnitsToTransfer,
      LocalDate accountingDate) {

    BigDecimal proportionalFiatValue =
        calculateProportionalFiatValue(contract, transferAmount, totalUnitsToTransfer);

    MemberCapitalEvent sellerEvent =
        MemberCapitalEvent.builder()
            .member(contract.getSeller())
            .type(transferAmount.type())
            .fiatValue(proportionalFiatValue.negate())
            .ownershipUnitAmount(totalUnitsToTransfer.negate())
            .accountingDate(accountingDate)
            .effectiveDate(accountingDate)
            .build();

    MemberCapitalEvent savedEvent = memberCapitalEventRepository.save(sellerEvent);

    CapitalTransferEventLink link =
        CapitalTransferEventLink.builder()
            .capitalTransferContract(contract)
            .memberCapitalEvent(savedEvent)
            .build();

    linkRepository.save(link);
  }

  private void createBuyerAcquisitionEvent(
      CapitalTransferContract contract,
      CapitalTransferAmount transferAmount,
      BigDecimal totalUnitsToTransfer,
      LocalDate accountingDate) {

    BigDecimal proportionalFiatValue =
        calculateProportionalFiatValue(contract, transferAmount, totalUnitsToTransfer);

    MemberCapitalEvent buyerEvent =
        MemberCapitalEvent.builder()
            .member(contract.getBuyer())
            .type(MemberCapitalEventType.CAPITAL_ACQUIRED)
            .fiatValue(proportionalFiatValue)
            .ownershipUnitAmount(totalUnitsToTransfer)
            .accountingDate(accountingDate)
            .effectiveDate(accountingDate)
            .build();

    MemberCapitalEvent savedEvent = memberCapitalEventRepository.save(buyerEvent);

    CapitalTransferEventLink link =
        CapitalTransferEventLink.builder()
            .capitalTransferContract(contract)
            .memberCapitalEvent(savedEvent)
            .build();

    linkRepository.save(link);
  }

  private BigDecimal getCurrentOwnershipUnitPrice() {
    AggregatedCapitalEvent latestEvent =
        aggregatedCapitalEventRepository.findTopByOrderByDateDesc();

    if (latestEvent == null || latestEvent.getOwnershipUnitPrice() == null) {
      throw new IllegalStateException("Could not determine current ownership unit price");
    }

    return latestEvent.getOwnershipUnitPrice();
  }

  private BigDecimal calculateUnitsToTransfer(
      CapitalTransferAmount transferAmount,
      BigDecimal currentUnitPrice,
      Map<MemberCapitalEventType, BigDecimal> sellerAvailableAmounts) {
    var sellerAvailableTotal = sellerAvailableAmounts.get(transferAmount.type());

    var differenceBetweenAmountAndTotal =
        sellerAvailableTotal.subtract(transferAmount.bookValue()).abs();

    // if difference is less than one cent, use seller available total of type
    if (differenceBetweenAmountAndTotal.compareTo(new BigDecimal("0.01")) < 0) {
      return sellerAvailableTotal.divide(currentUnitPrice, 5, RoundingMode.HALF_UP);
    }

    // TODO should this be half up?
    return transferAmount.bookValue().divide(currentUnitPrice, 5, RoundingMode.HALF_UP);
  }

  private BigDecimal calculateProportionalFiatValue(
      CapitalTransferContract contract,
      CapitalTransferAmount transferAmount,
      BigDecimal unitsToTransfer) {

    Long sellerId = contract.getSeller().getId();
    MemberCapitalEventType eventType = transferAmount.type();

    BigDecimal sellerTotalFiatValue =
        memberCapitalEventRepository.getTotalFiatValueByMemberIdAndType(sellerId, eventType);
    BigDecimal sellerTotalUnits =
        memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(sellerId, eventType);

    if (sellerTotalFiatValue == null
        || sellerTotalUnits == null
        || sellerTotalUnits.compareTo(BigDecimal.ZERO) == 0) {
      throw new IllegalStateException(
          String.format("Seller %d has no fiat value or units for type %s", sellerId, eventType));
    }

    return sellerTotalFiatValue
        .multiply(unitsToTransfer.abs())
        .divide(sellerTotalUnits, 5, RoundingMode.HALF_UP);
  }
}
