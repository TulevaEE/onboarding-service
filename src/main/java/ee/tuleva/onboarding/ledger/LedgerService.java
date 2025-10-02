package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount.CASH;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount.FUND_UNITS;

import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.SavingsFundLedger.UserAccount;
import ee.tuleva.onboarding.user.User;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.List;
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

    LedgerAccount cashAccount = ledgerAccountService.createUserAccount(party, CASH, ASSET, EUR);
    LedgerAccount fundUnitsAccount =
        ledgerAccountService.createUserAccount(party, FUND_UNITS, ASSET, FUND_UNIT);

    return ledgerAccountService.getAccounts(party);
  }

  @Transactional
  public LedgerAccount getUserAccount(User user, UserAccount accountName, AssetType assetType) {
    LedgerParty userParty =
        ledgerPartyService
            .getParty(user)
            .orElseThrow(() -> new IllegalStateException("User not onboarded"));

    return ledgerAccountService
        .getLedgerAccount(userParty, accountName, LIABILITY, assetType)
        .orElseThrow(() -> new IllegalStateException("User cash account not found"));
  }
}
