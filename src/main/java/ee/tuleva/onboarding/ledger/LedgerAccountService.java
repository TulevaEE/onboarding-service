package ee.tuleva.onboarding.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;

@Profile("dev")
@Service
@RequiredArgsConstructor
class LedgerAccountService {

  private final LedgerAccountRepository ledgerAccountRepository;

  public LedgerAccount createAccountForParty(LedgerParty ledgerParty) {
    var ledgerAccount = LedgerAccount.builder()
        .ledgerParty(ledgerParty)
        .assetTypeCode(EUR)
        .type(INCOME)
        .build();

    return ledgerAccountRepository.save(ledgerAccount);
  }

  public List<LedgerAccount> getAccountsByLedgerParty(LedgerParty ledgerParty) {
    return ledgerAccountRepository.findAllByLedgerParty(ledgerParty);
  }
}
