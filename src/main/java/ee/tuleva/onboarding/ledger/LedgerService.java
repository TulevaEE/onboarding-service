package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;

import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.user.User;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  public List<LedgerAccount> onboard(User user) {
    LedgerParty existingParty = ledgerPartyService.getParty(user).orElse(null);

    if (existingParty != null) {
      throw new IllegalStateException("User already onboarded");
    }

    LedgerParty party = ledgerPartyService.createParty(user);

    LedgerAccount cashAccount = ledgerAccountService.createAccount(party, EUR, ASSET);
    LedgerAccount fundUnitsAccount = ledgerAccountService.createAccount(party, FUND_UNIT, ASSET);

    return ledgerAccountService.getAccounts(party);
  }

  @Transactional
  public LedgerTransaction deposit(User user, BigDecimal amount, AssetType assetType) {
    LedgerParty userParty =
        ledgerPartyService
            .getParty(user)
            .orElseThrow(() -> new IllegalStateException("User not onboarded"));

    LedgerAccount userCashAccount =
        ledgerAccountService
            .getLedgerAccount(userParty, ASSET, assetType)
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
        Instant.now(clock),
        metadata,
        new LedgerEntryDto(userCashAccount, amount),
        new LedgerEntryDto(userCashAccount, amount.negate()) // Simplified for test
        );
  }
}
