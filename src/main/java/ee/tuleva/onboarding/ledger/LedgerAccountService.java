package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;

import java.util.List;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("dev")
@Service
@RequiredArgsConstructor
class LedgerAccountService {

  private final LedgerAccountRepository ledgerAccountRepository;

  LedgerAccount createAccountForParty(LedgerParty ledgerParty, String name, AssetType assetType, AccountType accountType) {
    var ledgerAccount =
        LedgerAccount.builder()
            .name(name)
            .ledgerParty(ledgerParty)
            .assetTypeCode(assetType)
            .type(accountType)
            .build();

    return ledgerAccountRepository.save(ledgerAccount);
  }

  public List<LedgerAccount> getAccountsByLedgerParty(LedgerParty ledgerParty) {
    return ledgerAccountRepository.findAllByLedgerParty(ledgerParty);
  }
}
