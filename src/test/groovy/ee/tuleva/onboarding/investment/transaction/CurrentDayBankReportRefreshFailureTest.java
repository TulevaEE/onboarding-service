package ee.tuleva.onboarding.investment.transaction;

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
import ee.tuleva.onboarding.banking.CurrentDayFetches;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.ResourceAccessException;

class CurrentDayBankReportRefreshFailureTest {

  private final BankAccounts bankAccounts = mock(BankAccounts.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final CurrentDayBankReportRefresh refresh =
      new CurrentDayBankReportRefresh(bankAccounts, new CurrentDayFetches(publisher));

  private final BankAccount deposit =
      new BankAccount("EE111111111111111111", DEPOSIT_EUR, TKF100, "client");
  private final BankAccount investment =
      new BankAccount("EE222222222222222222", FUND_INVESTMENT_EUR, TKF100, "client");
  private final BankAccount withdrawal =
      new BankAccount("EE333333333333333333", WITHDRAWAL_EUR, TKF100, "client");

  @Test
  void oneAccountsFailedFetchDoesNotCancelTheOthers() {
    given(bankAccounts.findAll(TKF100)).willReturn(List.of(deposit, investment));
    willThrow(new IllegalStateException("statement rejected"))
        .given(publisher)
        .publishEvent(new FetchSebCurrentDayTransactionsRequested(deposit));

    refresh.requestFor(TKF100);

    then(publisher).should().publishEvent(new FetchSebCurrentDayTransactionsRequested(investment));
  }

  @Test
  void anUnavailableGatewayStopsTheRemainingFetches() {
    given(bankAccounts.findAll(TKF100)).willReturn(List.of(deposit, investment, withdrawal));
    willThrow(new ResourceAccessException("gateway unreachable"))
        .given(publisher)
        .publishEvent(new FetchSebCurrentDayTransactionsRequested(investment));

    refresh.requestFor(TKF100);

    then(publisher)
        .should(never())
        .publishEvent(new FetchSebCurrentDayTransactionsRequested(withdrawal));
  }
}
