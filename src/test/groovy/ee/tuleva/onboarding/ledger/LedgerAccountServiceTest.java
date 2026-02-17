package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerAccountServiceTest {

  @Mock private LedgerAccountRepository ledgerAccountRepository;

  @InjectMocks private LedgerAccountService ledgerAccountService;

  @Test
  void countAccountsWithPositiveBalance_delegatesToRepository() {
    when(ledgerAccountRepository.countWithPositiveBalance(FUND_UNITS.name(), USER_ACCOUNT))
        .thenReturn(42);

    int count = ledgerAccountService.countAccountsWithPositiveBalance(FUND_UNITS);

    assertThat(count).isEqualTo(42);
  }
}
