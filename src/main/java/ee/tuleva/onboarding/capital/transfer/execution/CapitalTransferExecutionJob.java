package ee.tuleva.onboarding.capital.transfer.execution;

import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractRepository;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class CapitalTransferExecutionJob {

  private final CapitalTransferContractRepository contractRepository;
  private final CapitalTransferExecutor executor;

  @Scheduled(fixedRate = 10000) // every 10 seconds
  public void executeApprovedContracts() {
    List<CapitalTransferContract> approvedContracts =
        contractRepository.findAllByState(CapitalTransferContractState.APPROVED);

    if (approvedContracts.isEmpty()) {
      log.debug("No APPROVED capital transfer contracts found for execution");
      return;
    }

    log.info(
        "Found {} APPROVED capital transfer contracts for execution: {}",
        approvedContracts.size(),
        approvedContracts.stream().map(CapitalTransferContract::getId).toList());

    for (CapitalTransferContract contract : approvedContracts) {
      try {
        log.info("Executing capital transfer contract {}", contract.getId());
        executor.execute(contract);
        log.info("Successfully executed capital transfer contract {}", contract.getId());
      } catch (Exception e) {
        log.error(
            "Failed to execute capital transfer contract {} with exception: {}",
            contract.getId(),
            e.getMessage(),
            e);
      }
    }
  }
}
