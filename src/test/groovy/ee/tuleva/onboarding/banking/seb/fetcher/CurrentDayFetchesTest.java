package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayReportsForFundRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.ResourceAccessException;

class CurrentDayFetchesTest {

  private final BankAccounts bankAccounts = mock(BankAccounts.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final CurrentDayFetches fetches = new CurrentDayFetches(publisher, bankAccounts);

  private final BankAccount deposit =
      new BankAccount("EE111111111111111111", DEPOSIT_EUR, TKF100, "client");
  private final BankAccount investment =
      new BankAccount("EE222222222222222222", FUND_INVESTMENT_EUR, TKF100, "client");
  private final BankAccount withdrawal =
      new BankAccount("EE333333333333333333", WITHDRAWAL_EUR, TKF100, "client");

  @Test
  void aFundsRequestAsksForTheCurrentDayReportOfEveryAccountOfTheFund() {
    given(bankAccounts.findAll(TKF100)).willReturn(List.of(deposit, investment));

    fetches.onFundsCurrentDayReportsRequested(
        new FetchSebCurrentDayReportsForFundRequested(TKF100));

    then(publisher).should().publishEvent(new FetchSebCurrentDayTransactionsRequested(deposit));
    then(publisher).should().publishEvent(new FetchSebCurrentDayTransactionsRequested(investment));
  }

  @Test
  void oneAccountsFailedFetchDoesNotCancelTheOthers() {
    willThrow(new IllegalStateException("statement rejected"))
        .given(publisher)
        .publishEvent(new FetchSebCurrentDayTransactionsRequested(deposit));

    fetches.requestFor(List.of(deposit, investment));

    then(publisher).should().publishEvent(new FetchSebCurrentDayTransactionsRequested(investment));
  }

  @Test
  void anUnavailableGatewayStopsTheRemainingFetches() {
    willThrow(new ResourceAccessException("gateway unreachable"))
        .given(publisher)
        .publishEvent(new FetchSebCurrentDayTransactionsRequested(investment));

    fetches.requestFor(List.of(deposit, investment, withdrawal));

    then(publisher)
        .should(never())
        .publishEvent(new FetchSebCurrentDayTransactionsRequested(withdrawal));
  }
}
