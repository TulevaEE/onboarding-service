package ee.tuleva.onboarding.capital.transfer.execution;

import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.APPROVED_AND_NOTIFIED;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.CAPITAL_TRANSFER_APPROVED_BY_BOARD;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CapitalTransferExecutor {

  private static final BigDecimal CLAMPING_TOLERANCE = new BigDecimal("0.02");

  private final CapitalTransferContractRepository contractRepository;
  private final MemberCapitalEventRepository memberCapitalEventRepository;
  private final CapitalTransferValidator validator;
  private final CapitalTransferContractService contractService;
  private final CapitalTransferEventLinkRepository linkRepository;

  @Transactional
  public void execute(CapitalTransferContract contract) {
    log.info("Starting capital transfer execution for contract {}", contract.getId());

    validator.validateContract(contract);
    validator.validateSufficientCapital(contract);

    LocalDate accountingDate = LocalDate.now(ZoneId.of("Europe/Tallinn"));

    for (CapitalTransferAmount transferAmount : contract.getTransferAmounts()) {
      if (validator.shouldSkipTransfer(transferAmount)) {
        log.debug("Skipping transfer with zero/null book value for type {}", transferAmount.type());
        continue;
      }

      executeTransferAmount(contract, transferAmount, accountingDate);
    }

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
      CapitalTransferAmount transferAmount,
      LocalDate accountingDate) {

    BigDecimal requestedUnitsToTransfer =
        transferAmount
            .bookValue()
            .divide(transferAmount.ownershipUnitPrice(), 5, RoundingMode.HALF_UP);

    Long sellerId = contract.getSeller().getId();
    BigDecimal sellerAvailableUnits =
        memberCapitalEventRepository.getTotalOwnershipUnitsByMemberIdAndType(
            sellerId, transferAmount.type());

    BigDecimal totalUnitsToTransfer;
    if (shouldUseExactTotal(
        requestedUnitsToTransfer, sellerAvailableUnits, transferAmount.ownershipUnitPrice())) {
      totalUnitsToTransfer = sellerAvailableUnits;

      BigDecimal difference = requestedUnitsToTransfer.subtract(sellerAvailableUnits);
      BigDecimal valueDifference =
          difference
              .multiply(transferAmount.ownershipUnitPrice())
              .setScale(5, RoundingMode.HALF_UP);

      if (difference.compareTo(BigDecimal.ZERO) > 0) {
        log.warn(
            "Transfer request exceeds available capital for type {} by {} EUR. "
                + "Using exact available amount: {} units instead of requested {} units.",
            transferAmount.type(),
            valueDifference,
            sellerAvailableUnits,
            requestedUnitsToTransfer);
      } else {
        log.info(
            "Using exact total for type {} to avoid residuals. "
                + "Requested: {} units, Using: {} units (difference: {} EUR)",
            transferAmount.type(),
            requestedUnitsToTransfer,
            sellerAvailableUnits,
            valueDifference.abs());
      }
    } else if (sellerAvailableUnits != null
        && requestedUnitsToTransfer.compareTo(sellerAvailableUnits) > 0) {
      BigDecimal overdraftUnits = requestedUnitsToTransfer.subtract(sellerAvailableUnits);
      BigDecimal overdraftValue =
          overdraftUnits
              .multiply(transferAmount.ownershipUnitPrice())
              .setScale(5, RoundingMode.HALF_UP);

      throw new IllegalStateException(
          String.format(
              "Transfer request exceeds clamping tolerance of available capital for type %s. "
                  + "Requested: %s units, Available: %s units, Overdraft: %s EUR (max allowed: %s EUR). "
                  + "This indicates an error in application creation.",
              transferAmount.type(),
              requestedUnitsToTransfer,
              sellerAvailableUnits,
              overdraftValue,
              CLAMPING_TOLERANCE));
    } else {
      totalUnitsToTransfer = requestedUnitsToTransfer;
    }

    BigDecimal proportionalFiatValue =
        calculateProportionalFiatValue(contract, transferAmount, totalUnitsToTransfer);

    createSellerWithdrawalEvent(
        contract, transferAmount, totalUnitsToTransfer, proportionalFiatValue, accountingDate);
    createBuyerAcquisitionEvent(
        contract, transferAmount, totalUnitsToTransfer, proportionalFiatValue, accountingDate);
  }

  private void createSellerWithdrawalEvent(
      CapitalTransferContract contract,
      CapitalTransferAmount transferAmount,
      BigDecimal totalUnitsToTransfer,
      BigDecimal proportionalFiatValue,
      LocalDate accountingDate) {

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
      BigDecimal proportionalFiatValue,
      LocalDate accountingDate) {

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

  private boolean shouldUseExactTotal(
      BigDecimal requestedUnits, BigDecimal availableUnits, BigDecimal ownershipUnitPrice) {

    if (requestedUnits == null || availableUnits == null || ownershipUnitPrice == null) {
      return false;
    }

    BigDecimal unitDifference = requestedUnits.subtract(availableUnits).abs();
    BigDecimal valueDifference =
        unitDifference.multiply(ownershipUnitPrice).setScale(5, RoundingMode.HALF_UP);

    return valueDifference.compareTo(CLAMPING_TOLERANCE) <= 0;
  }
}
