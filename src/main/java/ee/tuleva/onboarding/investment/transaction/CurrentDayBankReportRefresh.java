package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.CurrentDayFetches;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class CurrentDayBankReportRefresh {

  private final BankAccounts bankAccounts;
  private final CurrentDayFetches currentDayFetches;

  @Async
  public void requestFor(TulevaFund fund) {
    log.info("Requesting SEB current-day reports: fund={}", fund);
    currentDayFetches.requestFor(bankAccounts.findAll(fund));
  }
}
