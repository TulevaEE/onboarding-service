package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.LedgerAccount.ServiceAccountType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile({"dev", "test"})
@Service
@RequiredArgsConstructor
class LedgerAccountService {

  private final LedgerAccountRepository ledgerAccountRepository;

  LedgerAccount createAccountForParty(
      LedgerParty ledgerParty, String name, AssetType assetType, AccountType accountType) {
    var ledgerAccount =
        LedgerAccount.builder()
            .name(name)
            .ledgerParty(ledgerParty)
            .assetTypeCode(assetType)
            .type(accountType)
            .build();

    return ledgerAccountRepository.save(ledgerAccount);
  }

  Optional<LedgerAccount> getLedgerAccountForParty(
      LedgerParty ledgerParty, AccountType accountType, AssetType assetTypeCode) {
    return Optional.of(
        ledgerAccountRepository.findByLedgerPartyAndTypeAndAssetTypeCode(
            ledgerParty, accountType, assetTypeCode));
  }

  List<LedgerAccount> getAccountsByLedgerParty(LedgerParty ledgerParty) {
    return ledgerAccountRepository.findAllByLedgerParty(ledgerParty);
  }

  LedgerAccount getServiceAccount(ServiceAccountType serviceAccountType) {
    return ledgerAccountRepository.findByServiceAccountType(serviceAccountType);
  }
}
