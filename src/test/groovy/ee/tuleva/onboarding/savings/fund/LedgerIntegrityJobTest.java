package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerIntegrityJobTest {

  @Mock SavingsFundLedger savingsFundLedger;
  @Mock OperationsNotificationService notificationService;
  @InjectMocks LedgerIntegrityJob job;

  @Test
  void staysQuietWhenEveryHolderAccountIsInCreditAndEveryPayoutMatchesItsPricing() {
    given(savingsFundLedger.findHolderAccountIdsInDebit()).willReturn(List.of());
    given(savingsFundLedger.findPayoutIdsBookedToAnotherPartyThanPriced()).willReturn(List.of());

    job.checkHolderAccounts();

    verifyNoInteractions(notificationService);
  }

  @Test
  void alertsOperationsWhenAHolderAccountIsInDebit() {
    var accountId = UUID.randomUUID();
    given(savingsFundLedger.findHolderAccountIdsInDebit()).willReturn(List.of(accountId));
    given(savingsFundLedger.findPayoutIdsBookedToAnotherPartyThanPriced()).willReturn(List.of());

    job.checkHolderAccounts();

    verify(notificationService)
        .sendMessage(contains("holder accounts in debit: count=1"), eq(SAVINGS), eq(ERROR));
    verify(notificationService).sendMessage(contains(accountId.toString()), eq(SAVINGS), eq(ERROR));
  }

  @Test
  void alertsOperationsWhenAPayoutWasBookedToAnotherPartyThanPriced() {
    given(savingsFundLedger.findHolderAccountIdsInDebit()).willReturn(List.of());
    given(savingsFundLedger.findPayoutIdsBookedToAnotherPartyThanPriced())
        .willReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

    job.checkHolderAccounts();

    verify(notificationService)
        .sendMessage(
            contains("redemption payouts booked to another party than priced: count=2"),
            eq(SAVINGS),
            eq(ERROR));
  }
}
