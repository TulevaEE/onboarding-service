package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.sampleLedgerAccount;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.party.PartyId;
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

  PartyId testParty = new PartyId(PartyId.Type.PERSON, "38812121215");
  LedgerParty ledgerParty = LedgerParty.builder().build();
  LedgerAccount account = sampleLedgerAccount().build();

  @Test
  void initializeAccounts_createsPartyAndAllAccounts() {
    when(ledgerPartyService.getOrCreate(testParty.code(), PERSON)).thenReturn(ledgerParty);

    for (var userAccount : UserAccount.values()) {
      when(ledgerAccountService.findUserAccount(ledgerParty, userAccount))
          .thenReturn(Optional.empty());
    }

    ledgerService.initializeAccounts(testParty);

    verify(ledgerPartyService).getOrCreate(testParty.code(), PERSON);
    for (var userAccount : UserAccount.values()) {
      verify(ledgerAccountService).createUserAccount(eq(ledgerParty), eq(userAccount));
    }
  }

  @Test
  void initializeAccounts_skipsExistingAccounts() {
    when(ledgerPartyService.getOrCreate(testParty.code(), PERSON)).thenReturn(ledgerParty);

    for (var userAccount : UserAccount.values()) {
      when(ledgerAccountService.findUserAccount(ledgerParty, userAccount))
          .thenReturn(Optional.of(account));
    }

    ledgerService.initializeAccounts(testParty);

    for (var userAccount : UserAccount.values()) {
      verify(ledgerAccountService, never()).createUserAccount(eq(ledgerParty), eq(userAccount));
    }
  }

  @Test
  void getPartyAccount_createsAccountWhenNotFound() {
    when(ledgerPartyService.getOrCreate(testParty.code(), PERSON)).thenReturn(ledgerParty);
    when(ledgerAccountService.findUserAccount(ledgerParty, SUBSCRIPTIONS))
        .thenReturn(Optional.empty());
    when(ledgerAccountService.createUserAccount(ledgerParty, SUBSCRIPTIONS)).thenReturn(account);

    assertThat(ledgerService.getPartyAccount(testParty.code(), PERSON, SUBSCRIPTIONS))
        .isEqualTo(account);
  }

  @Test
  void getPartyAccount_returnsExistingAccount() {
    when(ledgerPartyService.getOrCreate(testParty.code(), PERSON)).thenReturn(ledgerParty);
    when(ledgerAccountService.findUserAccount(ledgerParty, SUBSCRIPTIONS))
        .thenReturn(Optional.of(account));

    assertThat(ledgerService.getPartyAccount(testParty.code(), PERSON, SUBSCRIPTIONS))
        .isEqualTo(account);
  }

  @Test
  void getSystemAccount_createsWhenNotFound() {
    when(ledgerAccountService.findSystemAccount(INCOMING_PAYMENTS_CLEARING, TKF100))
        .thenReturn(Optional.empty());
    when(ledgerAccountService.createSystemAccount(INCOMING_PAYMENTS_CLEARING, TKF100))
        .thenReturn(account);

    assertThat(ledgerService.getSystemAccount(INCOMING_PAYMENTS_CLEARING, TKF100))
        .isEqualTo(account);
  }
}
