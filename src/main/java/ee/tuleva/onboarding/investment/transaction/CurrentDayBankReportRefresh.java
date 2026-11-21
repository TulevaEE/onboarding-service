package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayReportsForFundRequested;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
public class CurrentDayBankReportRefresh {

  private final ApplicationEventPublisher eventPublisher;

  @Async
  public void requestFor(TulevaFund fund) {
    eventPublisher.publishEvent(new FetchSebCurrentDayReportsForFundRequested(fund));
  }
}
