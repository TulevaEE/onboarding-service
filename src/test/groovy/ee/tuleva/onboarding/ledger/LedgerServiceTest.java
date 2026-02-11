package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.sampleLedgerAccount;
import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

  @Mock private LedgerPartyService ledgerPartyService;
  @Mock private LedgerAccountService ledgerAccountService;
  @InjectMocks private LedgerService ledgerService;

  User user = sampleUser().build();
  LedgerParty party = LedgerParty.builder().build();
  LedgerAccount account = sampleLedgerAccount().build();

  @Test
  void initializeUserAccounts_createsPartyAndAllAccounts() {
    when(ledgerPartyService.getParty(user)).thenReturn(Optional.empty());
    when(ledgerPartyService.createParty(user)).thenReturn(party);

    for (var userAccount : UserAccount.values()) {
      when(ledgerAccountService.findUserAccount(party, userAccount)).thenReturn(Optional.empty());
    }

    ledgerService.initializeUserAccounts(user);

    verify(ledgerPartyService).createParty(user);
    for (var userAccount : UserAccount.values()) {
      verify(ledgerAccountService).createUserAccount(eq(party), eq(userAccount));
    }
  }

  @Test
  void initializeUserAccounts_skipsIfPartyAlreadyExists() {
    when(ledgerPartyService.getParty(user)).thenReturn(Optional.of(party));

    for (var userAccount : UserAccount.values()) {
      when(ledgerAccountService.findUserAccount(party, userAccount))
          .thenReturn(Optional.of(account));
    }

    ledgerService.initializeUserAccounts(user);

    verify(ledgerPartyService, never()).createParty(user);
    for (var userAccount : UserAccount.values()) {
      verify(ledgerAccountService, never()).createUserAccount(eq(party), eq(userAccount));
    }
  }

  @Test
  void getUserAccount_createsPartyAndAccountWhenNotFound() {
    when(ledgerPartyService.getParty(user)).thenReturn(Optional.empty());
    when(ledgerPartyService.createParty(user)).thenReturn(party);
    when(ledgerAccountService.findUserAccount(party, SUBSCRIPTIONS)).thenReturn(Optional.empty());
    when(ledgerAccountService.createUserAccount(party, SUBSCRIPTIONS)).thenReturn(account);

    assertThat(ledgerService.getUserAccount(user, SUBSCRIPTIONS)).isEqualTo(account);
  }

  @Test
  void getUserAccount_returnsExistingPartyAndAccount() {
    when(ledgerPartyService.getParty(user)).thenReturn(Optional.of(party));
    when(ledgerAccountService.findUserAccount(party, SUBSCRIPTIONS))
        .thenReturn(Optional.of(account));

    assertThat(ledgerService.getUserAccount(user, SUBSCRIPTIONS)).isEqualTo(account);
  }

  @Test
  void getSystemAccount_createsWhenNotFound() {
    when(ledgerAccountService.findSystemAccount(INCOMING_PAYMENTS_CLEARING))
        .thenReturn(Optional.empty());
    when(ledgerAccountService.createSystemAccount(INCOMING_PAYMENTS_CLEARING)).thenReturn(account);

    assertThat(ledgerService.getSystemAccount(INCOMING_PAYMENTS_CLEARING)).isEqualTo(account);
  }
}
