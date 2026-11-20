package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class CurrentDayBankReportRefresh {

  private final BankAccounts bankAccounts;
  private final ApplicationEventPublisher eventPublisher;

  @Async
  public void requestFor(TulevaFund fund) {
    for (BankAccount account : bankAccounts.findAll(fund)) {
      try {
        log.info("Requesting SEB current-day report: account={}", account);
        eventPublisher.publishEvent(new FetchSebCurrentDayTransactionsRequested(account));
      } catch (RuntimeException exception) {
        log.error("SEB current-day report request failed: account={}", account, exception);
        if (isGatewayUnavailable(exception)) {
          return;
        }
      }
    }
  }

  private static boolean isGatewayUnavailable(RuntimeException exception) {
    return exception instanceof HttpServerErrorException
        || exception instanceof ResourceAccessException;
  }
}
