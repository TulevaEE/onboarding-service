package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional
class LedgerAccountRepositoryTest {

  @Autowired LedgerAccountRepository accountRepository;
  @Autowired LedgerPartyRepository partyRepository;
  @Autowired LedgerTransactionRepository transactionRepository;

  private LedgerAccount systemCounterparty;

  @BeforeEach
  void setUp() {
    systemCounterparty =
        accountRepository.save(
            LedgerAccount.builder()
                .name("Fund units issued")
                .purpose(SYSTEM_ACCOUNT)
                .accountType(ASSET)
                .owner(null)
                .assetType(FUND_UNIT)
                .build());
  }

  @Test
  void countWithPositiveBalance_countsOnlyUserAccountsHoldingUnits() {
    LedgerAccount holdingUnits = userFundUnitsAccount("TEST-PERSON-001");
    LedgerAccount fullyRedeemed = userFundUnitsAccount("TEST-PERSON-002");
    userFundUnitsAccount("TEST-PERSON-003");

    record(holdingUnits, new BigDecimal("-10.00000"));
    record(fullyRedeemed, new BigDecimal("-5.00000"));
    record(fullyRedeemed, new BigDecimal("5.00000"));

    int count = accountRepository.countWithPositiveBalance("FUND_UNITS", USER_ACCOUNT);

    assertThat(count).isEqualTo(1);
  }

  private LedgerAccount userFundUnitsAccount(String ownerId) {
    LedgerParty party =
        partyRepository.save(
            LedgerParty.builder()
                .partyType(LEGAL_ENTITY)
                .ownerId(ownerId)
                .details(Map.of("name", "Test Party"))
                .build());
    return accountRepository.save(
        LedgerAccount.builder()
            .name("FUND_UNITS")
            .purpose(USER_ACCOUNT)
            .accountType(LIABILITY)
            .owner(party)
            .assetType(FUND_UNIT)
            .build());
  }

  private void record(LedgerAccount account, BigDecimal amount) {
    LedgerTransaction transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(Instant.now())
            .metadata(new HashMap<>())
            .build();
    transaction.addEntry(account, amount);
    transaction.addEntry(systemCounterparty, amount.negate());
    transactionRepository.saveAndFlush(transaction);
  }
}
