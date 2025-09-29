package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile({"dev", "test"})
@Service
@RequiredArgsConstructor
public class LedgerAccountService {

  private final LedgerAccountRepository ledgerAccountRepository;

  LedgerAccount createAccountForParty(
      LedgerParty ledgerParty, String name, AssetType assetType, AccountType accountType) {
    var ledgerAccount =
        LedgerAccount.builder()
            .name(name)
            .ledgerParty(ledgerParty)
            .accountPurpose(AccountPurpose.USER_ACCOUNT)
            .assetTypeCode(assetType)
            .type(accountType)
            .build();

    return ledgerAccountRepository.save(ledgerAccount);
  }

  public Optional<LedgerAccount> getLedgerAccountForParty(
      LedgerParty ledgerParty, AccountType accountType, AssetType assetTypeCode) {
    return Optional.of(
        ledgerAccountRepository.findByLedgerPartyAndTypeAndAssetTypeCode(
            ledgerParty, accountType, assetTypeCode));
  }

  List<LedgerAccount> getAccountsByLedgerParty(LedgerParty ledgerParty) {
    return ledgerAccountRepository.findAllByLedgerParty(ledgerParty);
  }

  public Optional<LedgerAccount> findSystemAccount(
      String name, AccountPurpose accountPurpose, AssetType assetType, AccountType accountType) {
    return ledgerAccountRepository.findByNameAndAccountPurposeAndAssetTypeCodeAndType(
        name, accountPurpose, assetType, accountType);
  }

  public LedgerAccount createSystemAccount(
      String name, AccountPurpose accountPurpose, AssetType assetType, AccountType accountType) {
    var ledgerAccount =
        LedgerAccount.builder()
            .name(name)
            .accountPurpose(accountPurpose)
            .assetTypeCode(assetType)
            .type(accountType)
            .build();

    return ledgerAccountRepository.save(ledgerAccount);
  }
}
