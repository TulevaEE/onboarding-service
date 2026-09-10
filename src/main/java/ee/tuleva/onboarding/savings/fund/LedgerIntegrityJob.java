package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class LedgerIntegrityJob {

  private static final int IDS_IN_MESSAGE = 5;

  private final SavingsFundLedger savingsFundLedger;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 30 6 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "LedgerIntegrityJob", lockAtMostFor = "30m", lockAtLeastFor = "1m")
  public void checkHolderAccounts() {
    report("holder accounts in debit", savingsFundLedger.findHolderAccountIdsInDebit());
    report(
        "redemption payouts booked to another party than priced",
        savingsFundLedger.findPayoutIdsBookedToAnotherPartyThanPriced());
  }

  private void report(String problem, List<UUID> ids) {
    if (ids.isEmpty()) {
      log.info("Ledger integrity check passed: check={}", problem);
      return;
    }
    String message =
        "LEDGER INTEGRITY: %s: count=%d, ids=%s"
            .formatted(problem, ids.size(), ids.stream().limit(IDS_IN_MESSAGE).toList());
    log.error(message);
    notificationService.sendMessage(message, SAVINGS, ERROR);
  }
}
