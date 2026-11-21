package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.seb.fetcher.SebGatewayAvailability.isUnavailable;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayReportsForFundRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

@Slf4j
@RequiredArgsConstructor
public class CurrentDayFetches {

  private final ApplicationEventPublisher eventPublisher;
  private final BankAccounts bankAccounts;

  @EventListener
  public void onFundsCurrentDayReportsRequested(FetchSebCurrentDayReportsForFundRequested event) {
    log.info("Requesting SEB current-day reports: fund={}", event.fund());
    requestFor(bankAccounts.findAll(event.fund()));
  }

  public void requestFor(List<BankAccount> accounts) {
    if (accounts.isEmpty()) {
      return;
    }
    List<BankAccount> remaining = accounts.subList(1, accounts.size());
    if (requestStoppedByAnOutage(accounts.getFirst())) {
      if (!remaining.isEmpty()) {
        log.warn(
            "SEB gateway unavailable, not requesting the remaining current-day reports:"
                + " accounts={}",
            remaining);
      }
      return;
    }
    requestFor(remaining);
  }

  private boolean requestStoppedByAnOutage(BankAccount account) {
    try {
      eventPublisher.publishEvent(new FetchSebCurrentDayTransactionsRequested(account));
      return false;
    } catch (RuntimeException exception) {
      log.error("SEB current day transactions fetch failed: account={}", account, exception);
      return isUnavailable(exception);
    }
  }
}
