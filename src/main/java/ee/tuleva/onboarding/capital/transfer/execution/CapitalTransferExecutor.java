package ee.tuleva.onboarding.capital.transfer.execution;

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent;
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractRepository;
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

  private final CapitalTransferContractRepository contractRepository;
  private final MemberCapitalEventRepository memberCapitalEventRepository;
  private final AggregatedCapitalEventRepository aggregatedCapitalEventRepository;
  private final CapitalTransferValidator validator;

  @Transactional
  public void execute(CapitalTransferContract contract) {
    log.info("Starting capital transfer execution for contract {}", contract.getId());

    validator.validateContract(contract);
    validator.validateSufficientCapital(contract);

    BigDecimal currentUnitPrice = getCurrentOwnershipUnitPrice();
    LocalDate accountingDate = LocalDate.now(ZoneId.of("Europe/Tallinn"));

    for (CapitalTransferAmount transferAmount : contract.getTransferAmounts()) {
      if (validator.shouldSkipTransfer(transferAmount)) {
        log.debug("Skipping transfer with zero/null book value for type {}", transferAmount.type());
        continue;
      }

      executeTransferAmount(contract, transferAmount, currentUnitPrice, accountingDate);
    }

    contract.executed();
    contractRepository.save(contract);

    log.info(
        "Capital transfer {} executed successfully. Transferred capital from member {} to member {}",
        contract.getId(),
        contract.getSeller().getId(),
        contract.getBuyer().getId());
  }

  private void executeTransferAmount(
      CapitalTransferContract contract,
      CapitalTransferAmount transferAmount,
      BigDecimal currentUnitPrice,
      LocalDate accountingDate) {

    BigDecimal totalUnitsToTransfer =
        transferAmount.bookValue().divide(currentUnitPrice, 5, RoundingMode.HALF_UP);

    createSellerWithdrawalEvent(contract, transferAmount, totalUnitsToTransfer, accountingDate);
    createBuyerAcquisitionEvent(contract, transferAmount, totalUnitsToTransfer, accountingDate);
  }

  private void createSellerWithdrawalEvent(
      CapitalTransferContract contract,
      CapitalTransferAmount transferAmount,
      BigDecimal totalUnitsToTransfer,
      LocalDate accountingDate) {

    MemberCapitalEvent sellerEvent =
        MemberCapitalEvent.builder()
            .member(contract.getSeller())
            .type(transferAmount.type())
            .fiatValue(transferAmount.bookValue().negate())
            .ownershipUnitAmount(totalUnitsToTransfer.negate())
            .accountingDate(accountingDate)
            .effectiveDate(accountingDate)
            .build();

    memberCapitalEventRepository.save(sellerEvent);
  }

  private void createBuyerAcquisitionEvent(
      CapitalTransferContract contract,
      CapitalTransferAmount transferAmount,
      BigDecimal totalUnitsToTransfer,
      LocalDate accountingDate) {

    MemberCapitalEvent buyerEvent =
        MemberCapitalEvent.builder()
            .member(contract.getBuyer())
            .type(MemberCapitalEventType.CAPITAL_ACQUIRED)
            .fiatValue(transferAmount.bookValue())
            .ownershipUnitAmount(totalUnitsToTransfer)
            .accountingDate(accountingDate)
            .effectiveDate(accountingDate)
            .build();

    memberCapitalEventRepository.save(buyerEvent);
  }

  private BigDecimal getCurrentOwnershipUnitPrice() {
    AggregatedCapitalEvent latestEvent =
        aggregatedCapitalEventRepository.findTopByOrderByDateDesc();

    if (latestEvent == null || latestEvent.getOwnershipUnitPrice() == null) {
      throw new IllegalStateException("Could not determine current ownership unit price");
    }

    return latestEvent.getOwnershipUnitPrice();
  }
}
