package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class CurrentDayBankReportRefresh {

  private final BankAccounts bankAccounts;
  private final ApplicationEventPublisher eventPublisher;

  @Async
  public void requestFor(TulevaFund fund) {
    bankAccounts
        .findAll(fund)
        .forEach(
            account -> {
              log.info("Requesting SEB current-day report: account={}", account);
              eventPublisher.publishEvent(new FetchSebCurrentDayTransactionsRequested(account));
            });
  }
}
