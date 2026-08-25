package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserUnitBalanceGuardTest {

  @Mock private LedgerAccountRepository ledgerAccountRepository;

  private UserUnitBalanceGuard guard;

  @BeforeEach
  void setUp() {
    guard = new UserUnitBalanceGuard(ledgerAccountRepository);
    ReflectionTestUtils.setField(guard, "enforce", true);
  }

  @Test
  void redeemingMoreUnitsThanTheAccountHoldsIsRefused() {
    // The account is a liability: holding 10 units means a balance of -10. Taking 15 out posts +15,
    // which would leave it at +5 -- units nobody ever had.
    var account = unitAccount();
    when(ledgerAccountRepository.balanceOf(account)).thenReturn(new BigDecimal("-10.00000"));

    assertThatThrownBy(() -> guard.check(entryOn(account, new BigDecimal("15.00000"))))
        .isInstanceOf(UnitBalanceViolationException.class);
  }

  @Test
  void redeemingExactlyWhatTheAccountHoldsIsAllowed() {
    var account = unitAccount();
    when(ledgerAccountRepository.balanceOf(account)).thenReturn(new BigDecimal("-10.00000"));

    assertThatCode(() -> guard.check(entryOn(account, new BigDecimal("10.00000"))))
        .doesNotThrowAnyException();
  }

  @Test
  void addingUnitsIsNeverChecked() {
    var account = unitAccount();

    guard.check(entryOn(account, new BigDecimal("-10.00000")));

    verify(ledgerAccountRepository, never()).balanceOf(any());
  }

  @Test
  void cashAccountsAreNotGuardedByThisRule() {
    var account = cashAccount();

    guard.check(entryOn(account, new BigDecimal("15.00")));

    verify(ledgerAccountRepository, never()).balanceOf(any());
  }

  @Test
  void withoutEnforcementTheViolationIsReportedButNotBlocked() {
    ReflectionTestUtils.setField(guard, "enforce", false);
    var account = unitAccount();
    when(ledgerAccountRepository.balanceOf(account)).thenReturn(new BigDecimal("-10.00000"));

    assertThatCode(() -> guard.check(entryOn(account, new BigDecimal("15.00000"))))
        .doesNotThrowAnyException();
  }

  private static LedgerTransactionService.LedgerEntryDto entryOn(
      LedgerAccount account, BigDecimal amount) {
    return new LedgerTransactionService.LedgerEntryDto(account, amount);
  }

  private static LedgerAccount unitAccount() {
    return account(FUND_UNIT);
  }

  private static LedgerAccount cashAccount() {
    return account(EUR);
  }

  private static LedgerAccount account(LedgerAccount.AssetType assetType) {
    var owner = new LedgerParty();
    var account =
        LedgerAccount.builder()
            .name("FUND_UNITS_RESERVED")
            .owner(owner)
            .assetType(assetType)
            .accountType(LIABILITY)
            .purpose(USER_ACCOUNT)
            .build();
    ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
    return account;
  }
}
