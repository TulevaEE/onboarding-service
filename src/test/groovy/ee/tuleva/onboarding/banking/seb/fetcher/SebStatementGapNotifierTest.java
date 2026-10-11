package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebStatementGapNotifierTest {

  private static final SebStatementGapsFound GAPS =
      new SebStatementGapsFound(
          List.of(
              new StatementGap(
                  new BankAccount("EE111111111111111111", DEPOSIT_EUR, TKF100, "gw-test"),
                  new StatementPeriod(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13))),
              new StatementGap(
                  new BankAccount("EE222222222222222222", FUND_INVESTMENT_EUR, TUV100, "gw-test"),
                  new StatementPeriod(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12)))));

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private SebStatementGapNotifier notifier;

  @Test
  void onGapsFound_sendsOneMessageListingEveryGapAndTheCatchUpCommand() {
    notifier.onGapsFound(GAPS);

    then(notificationService)
        .should()
        .sendMessage(
            "🔴 SEB statements not booked in the ledger: "
                + "TKF100:DEPOSIT_EUR 2026-09-13, TUV100:FUND_INVESTMENT_EUR 2026-09-11 to 2026-09-12. "
                + "Missing statements are re-fetched every 30 minutes until 23:30; "
                + "a statement that failed processing has failed_at set on its banking_message. "
                + "If this persists, run POST /admin/fetch-seb-history?from=2026-09-11&to=2026-09-13 <!channel>",
            SAVINGS);
  }

  @Test
  void onGapsFound_doesNotThrowWhenTheNotificationCannotBeSent() {
    willThrow(new RuntimeException("Slack is down"))
        .given(notificationService)
        .sendMessage(any(), any());

    assertThatCode(() -> notifier.onGapsFound(GAPS)).doesNotThrowAnyException();
  }
}
