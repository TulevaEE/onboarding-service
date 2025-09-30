package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.*;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;

import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.user.User;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

    LedgerParty party =
        ledgerPartyService.createPartyForUser(user, "Party of " + user.getPersonalCode());

    LedgerAccount cashAccount =
        ledgerAccountService.createAccount(
            party, "Cash account for " + user.getPersonalCode(), EUR, INCOME);
    LedgerAccount stockAccount =
        ledgerAccountService.createAccount(
            party, "Stock account for " + user.getPersonalCode(), FUND_UNIT, ASSET);

    return ledgerAccountService.getAccounts(party);
  }

  @Transactional
  public LedgerTransaction deposit(User user, BigDecimal amount, AssetType assetType) {
    LedgerParty userParty =
        ledgerPartyService
            .getPartyForUser(user)
            .orElseThrow(() -> new IllegalStateException("User not onboarded"));

    LedgerAccount userCashAccount =
        ledgerAccountService
            .getLedgerAccount(userParty, INCOME, EUR)
            .orElseThrow(() -> new IllegalStateException("User cash account not found"));

    if (userCashAccount.getAssetType() != assetType) {
      throw new IllegalArgumentException("Invalid asset type provided for given account");
    }

    // This is just a test method - in real usage, use SavingsFundLedgerService
    Map<String, Object> metadata =
        Map.of(
            "operationType", "TEST_DEPOSIT",
            "userId", user.getId(),
            "personalCode", user.getPersonalCode());

    return ledgerTransactionService.createTransaction(
        TRANSFER,
        metadata,
        new LedgerEntryDto(userCashAccount, amount),
        new LedgerEntryDto(userCashAccount, amount.negate()) // Simplified for test
        );
  }
}
