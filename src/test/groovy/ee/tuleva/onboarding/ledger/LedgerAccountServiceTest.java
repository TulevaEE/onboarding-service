package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.fundUnitsAccountWithBalance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
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
  void countAccountsWithPositiveBalance_countsOnlyAccountsWithPositiveBalance() {
    var accountWithUnits = fundUnitsAccountWithBalance(new BigDecimal("100.00000"));
    var accountWithZero = fundUnitsAccountWithBalance(BigDecimal.ZERO);
    var anotherAccountWithUnits = fundUnitsAccountWithBalance(new BigDecimal("50.00000"));

    when(ledgerAccountRepository.findAllByNameAndPurpose(
            UserAccount.FUND_UNITS.name(), USER_ACCOUNT))
        .thenReturn(List.of(accountWithUnits, accountWithZero, anotherAccountWithUnits));

    int count = ledgerAccountService.countAccountsWithPositiveBalance(UserAccount.FUND_UNITS);

    assertThat(count).isEqualTo(2);
  }
}
