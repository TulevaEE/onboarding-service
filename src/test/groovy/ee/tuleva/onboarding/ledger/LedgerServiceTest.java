package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.sampleLedgerAccount;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

  @Mock private LedgerPartyService ledgerPartyService;
  @Mock private LedgerAccountService ledgerAccountService;
  @InjectMocks private LedgerService ledgerService;

  User user = sampleUser().build();
  LedgerParty party = LedgerParty.builder().build();
  LedgerAccount account = sampleLedgerAccount().build();

  @Test
  void getUserAccount_createsPartyAndAccountWhenNotFound() {
    when(ledgerPartyService.getParty(user))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(party));
    when(ledgerAccountService.findUserAccount(party, SUBSCRIPTIONS))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(account));

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
  void getUserAccount_retriesPartyLookupOnConstraintViolation() {
    when(ledgerPartyService.getParty(user))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(party));
    when(ledgerPartyService.createParty(user))
        .thenThrow(new DataIntegrityViolationException("ux_party_type_owner"));
    when(ledgerAccountService.findUserAccount(party, SUBSCRIPTIONS))
        .thenReturn(Optional.of(account));

    assertThat(ledgerService.getUserAccount(user, SUBSCRIPTIONS)).isEqualTo(account);
  }

  @Test
  void getUserAccount_retriesAccountLookupOnConstraintViolation() {
    when(ledgerPartyService.getParty(user)).thenReturn(Optional.of(party));
    when(ledgerAccountService.findUserAccount(party, SUBSCRIPTIONS))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(account));
    when(ledgerAccountService.createUserAccount(party, SUBSCRIPTIONS))
        .thenThrow(new DataIntegrityViolationException("ux_account_owner_name"));

    assertThat(ledgerService.getUserAccount(user, SUBSCRIPTIONS)).isEqualTo(account);
  }

  @Test
  void getSystemAccount_retriesOnConstraintViolation() {
    var systemAccount = SystemAccount.INCOMING_PAYMENTS_CLEARING;

    when(ledgerAccountService.findSystemAccount(systemAccount))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(account));
    when(ledgerAccountService.createSystemAccount(systemAccount))
        .thenThrow(new DataIntegrityViolationException("ux_account_system_name"));

    assertThat(ledgerService.getSystemAccount(systemAccount)).isEqualTo(account);
  }
}
