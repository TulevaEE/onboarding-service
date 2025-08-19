package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.APPROVED;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.APPROVED_AND_NOTIFIED;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.CAPITAL_TRANSFER_APPROVED_BY_BOARD;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!dev")
@Slf4j
public class ApprovedByBoardEmailSender {

  private final CapitalTransferContractRepository capitalTransferContractRepository;
  private final CapitalTransferContractService capitalTransferContractService;

  // every 5 minutes
  @Scheduled(cron = "0 */5 * * * *", zone = "Europe/Tallinn")
  public void sendBoardApprovedEmails() {
    var approvedByBoardCapitalTransfers =
        capitalTransferContractRepository.findAllByState(APPROVED);

    if (approvedByBoardCapitalTransfers.isEmpty()) {
      log.info("No APPROVED capital transfer contracts found");
      return;
    }
    log.info(
        "Need to send {} emails about capital transfer contract board approval",
        approvedByBoardCapitalTransfers.size() * 2);

    for (var transfer : approvedByBoardCapitalTransfers) {
      try {
        log.info("Sending approval emails for capital transfer (id={})", transfer.getId());
        capitalTransferContractService.sendContractEmail(
            transfer.getBuyer().getUser(), CAPITAL_TRANSFER_APPROVED_BY_BOARD, transfer);
        capitalTransferContractService.sendContractEmail(
            transfer.getSeller().getUser(), CAPITAL_TRANSFER_APPROVED_BY_BOARD, transfer);

        capitalTransferContractService.updateStateBySystem(transfer.getId(), APPROVED_AND_NOTIFIED);
      } catch (Exception e) {
        log.error(
            "Failed to send email for capital transfer (id={}) with exception: {}",
            transfer.getId(),
            e.toString());
      }
    }
  }
}
