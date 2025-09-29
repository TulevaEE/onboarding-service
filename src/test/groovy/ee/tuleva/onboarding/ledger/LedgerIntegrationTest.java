package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.USER;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import ee.tuleva.onboarding.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LedgerIntegrationTest {
  @Autowired private LedgerService ledgerService;

  @Autowired private LedgerAccountRepository ledgerAccountRepository;

  @Autowired private LedgerPartyRepository ledgerPartyRepository;

  @BeforeEach
  void setup() {
    ledgerAccountRepository.save(
        LedgerAccount.builder()
            .name("DEPOSIT_EUR")
            .accountPurpose(SYSTEM_ACCOUNT)
            .type(INCOME)
            .assetTypeCode(EUR)
            .build());
  }

  @AfterEach
  void cleanup() {
    ledgerAccountRepository.deleteAll();
    ledgerPartyRepository.deleteAll();
  }

  @Test
  @DisplayName("should onboard user")
  public void shouldOnboardUser() {
    User user = sampleUser().build();

    ledgerService.onboardUser(user);

    var party = ledgerPartyRepository.findByOwnerId(user.getPersonalCode());

    assertThat(party.getOwnerId()).isEqualTo(user.getPersonalCode());
    assertThat(party.getType()).isEqualTo(USER);

    var accounts = ledgerAccountRepository.findAllByLedgerParty(party);

    assertThat(accounts.size()).isEqualTo(2);

    var cashAccount =
        accounts.stream()
            .filter(
                account ->
                    account.getType() == LedgerAccount.AccountType.INCOME
                        && account.getAssetTypeCode() == LedgerAccount.AssetType.EUR)
            .findFirst()
            .orElseThrow();
    var stockAccount =
        accounts.stream()
            .filter(
                account ->
                    account.getType() == LedgerAccount.AccountType.ASSET
                        && account.getAssetTypeCode() == LedgerAccount.AssetType.FUND_UNIT)
            .findFirst()
            .orElseThrow();

    assertThat(cashAccount).isNotNull();
    assertThat(stockAccount).isNotNull();
  }
}
