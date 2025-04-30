package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.ServiceAccountType.DEPOSIT_EUR;

import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.user.User;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile({"dev", "test"})
@Service
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;

  public List<LedgerAccount> onboardUser(User user) {
    LedgerParty existingParty = ledgerPartyService.getPartyForUser(user).orElse(null);

    if (existingParty != null) {
      throw new IllegalStateException("User already onboarded");
    }

    LedgerParty party = ledgerPartyService.createPartyForUser(user);

    LedgerAccount cashAccount =
        ledgerAccountService.createAccountForParty(
            party, "Cash account for " + user.getPersonalCode(), EUR, INCOME);
    LedgerAccount stockAccount =
        ledgerAccountService.createAccountForParty(
            party, "Stock account for " + user.getPersonalCode(), UNIT, ASSET);

    return ledgerAccountService.getAccountsByLedgerParty(party);
  }

  @Transactional
  public LedgerTransaction deposit(User user, BigDecimal amount, AssetType assetType) {
    LedgerParty userParty =
        ledgerPartyService
            .getPartyForUser(user)
            .orElseThrow(() -> new IllegalStateException("User not onboarded"));

    LedgerAccount userCashAccount =
        ledgerAccountService
            .getLedgerAccountForParty(userParty, INCOME, EUR)
            .orElseThrow(() -> new IllegalStateException("User cash account not found"));

    if (userCashAccount.getAssetTypeCode() != assetType) {
      throw new IllegalArgumentException("Invalid asset type provided for given account");
    }

    LedgerAccount depositServiceAccount = ledgerAccountService.getServiceAccount(DEPOSIT_EUR);

    LedgerEntryDto userDepositEntry = new LedgerEntryDto(userCashAccount, amount);
    LedgerEntryDto tulevaDepositEntry = new LedgerEntryDto(depositServiceAccount, amount.negate());

    return ledgerTransactionService.createTransaction(
        "Deposit for " + user.getPersonalCode(), List.of(userDepositEntry, tulevaDepositEntry));
  }
}
