package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.ServiceAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.USER;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
public class LedgerTransactionIntegrationTest {
  @Autowired private LedgerService ledgerService;

  @Autowired private LedgerAccountRepository ledgerAccountRepository;
  @Autowired private LedgerAccountService ledgerAccountService;

  @Autowired private LedgerPartyRepository ledgerPartyRepository;

  @Autowired private LedgerEntryRepository ledgerEntryRepository;

  @Autowired private LedgerTransactionRepository ledgerTransactionRepository;

  @Autowired private LedgerTransactionService ledgerTransactionService;

  @BeforeEach
  void setup() {
    User user = sampleUser().build();
    ledgerService.onboardUser(user);

    ledgerAccountRepository.save(
        LedgerAccount.builder()
            .name("Tuleva cash deposit")
            .serviceAccountType(DEPOSIT_EUR)
            .type(INCOME)
            .assetTypeCode(EUR)
            .build());
  }

  @AfterEach
  void cleanup() {
    ledgerEntryRepository.deleteAll();
    ledgerTransactionRepository.deleteAll();
    ledgerAccountRepository.deleteAll();
    ledgerPartyRepository.deleteAll();
  }

  private LedgerAccount getCashAccount(LedgerParty party) {
    return ledgerAccountService.getLedgerAccountForParty(party, INCOME, EUR).orElseThrow();
  }

  private LedgerAccount getServiceAccount() {
    return ledgerAccountService.getServiceAccount(DEPOSIT_EUR);
  }

  @Test
  @DisplayName("should create transaction")
  public void shouldCreateTransaction() {
    User user = sampleUser().build();

    var party = ledgerPartyRepository.findByOwnerId(user.getPersonalCode());

    assertThat(party.getOwnerId()).isEqualTo(user.getPersonalCode());
    assertThat(party.getType()).isEqualTo(USER);

    var cashAccount = getCashAccount(party);
    var serviceAccount = getServiceAccount();

    ledgerTransactionService.createTransaction(
        "Test transaction",
        List.of(
            new LedgerEntryDto(cashAccount, new BigDecimal("1000.00")),
            new LedgerEntryDto(serviceAccount, new BigDecimal("-1000.00"))));

    assertThat(getCashAccount(party).getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(getCashAccount(party).getEntries().size()).isEqualTo(1);

    assertThat(getServiceAccount().getBalance()).isEqualByComparingTo(new BigDecimal("-1000.00"));
    assertThat(getServiceAccount().getEntries().size()).isEqualTo(1);

    ledgerTransactionService.createTransaction(
        "Test transaction 2",
        List.of(
            new LedgerEntryDto(cashAccount, new BigDecimal("-1000.00")),
            new LedgerEntryDto(serviceAccount, new BigDecimal("1000.00"))));

    assertThat(getCashAccount(party).getBalance()).isEqualByComparingTo(new BigDecimal("0"));
    assertThat(getCashAccount(party).getEntries().size()).isEqualTo(2);

    assertThat(getServiceAccount().getBalance()).isEqualByComparingTo(new BigDecimal("0"));
    assertThat(getServiceAccount().getEntries().size()).isEqualTo(2);
  }
}
