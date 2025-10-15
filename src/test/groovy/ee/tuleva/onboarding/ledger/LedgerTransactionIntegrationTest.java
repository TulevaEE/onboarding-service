package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.USER;
import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.ledger.UserAccount.CASH;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
public class LedgerTransactionIntegrationTest {

  @Autowired private LedgerService ledgerService;
  @Autowired private LedgerAccountRepository ledgerAccountRepository;
  @Autowired private LedgerAccountService ledgerAccountService;
  @Autowired private LedgerPartyRepository ledgerPartyRepository;
  @Autowired private LedgerTransactionRepository ledgerTransactionRepository;
  @Autowired private LedgerTransactionService ledgerTransactionService;
  @Autowired private Clock clock;

  @BeforeEach
  void setup() {
    User user = sampleUser().build();
    ledgerService.onboard(user);

    ledgerAccountRepository.save(
        LedgerAccount.builder()
            .name(INCOMING_PAYMENTS_CLEARING.name())
            .purpose(SYSTEM_ACCOUNT)
            .accountType(INCOMING_PAYMENTS_CLEARING.getAccountType())
            .assetType(INCOMING_PAYMENTS_CLEARING.getAssetType())
            .build());
  }

  @AfterEach
  void cleanup() {
    ledgerTransactionRepository.deleteAll();
    ledgerAccountRepository.deleteAll();
    ledgerPartyRepository.deleteAll();
  }

  private LedgerAccount getCashAccount(LedgerParty party) {
    return ledgerAccountService.findUserAccount(party, CASH).orElseThrow();
  }

  private LedgerAccount getServiceAccount() {
    return ledgerAccountService.findSystemAccount(INCOMING_PAYMENTS_CLEARING).orElseThrow();
  }

  @Test
  @DisplayName("should create transaction")
  public void shouldCreateTransaction() {
    User user = sampleUser().build();

    var party = ledgerPartyRepository.findByOwnerId(user.getPersonalCode());

    assertThat(party.getOwnerId()).isEqualTo(user.getPersonalCode());
    assertThat(party.getPartyType()).isEqualTo(USER);

    var cashAccount = getCashAccount(party);
    var serviceAccount = getServiceAccount();

    ledgerTransactionService.createTransaction(
        Instant.now(clock),
        Map.of("operationType", "TEST_TRANSACTION"),
        new LedgerEntryDto(cashAccount, new BigDecimal("1000.00")),
        new LedgerEntryDto(serviceAccount, new BigDecimal("-1000.00")));

    assertThat(getCashAccount(party).getBalance()).isEqualByComparingTo("1000.00");
    assertThat(getCashAccount(party).getEntries().size()).isEqualTo(1);

    assertThat(getServiceAccount().getBalance()).isEqualByComparingTo("-1000.00");
    assertThat(getServiceAccount().getEntries().size()).isEqualTo(1);

    ledgerTransactionService.createTransaction(
        Instant.now(clock),
        Map.of("operationType", "TEST_TRANSACTION_2"),
        new LedgerEntryDto(cashAccount, new BigDecimal("-1000.00")),
        new LedgerEntryDto(serviceAccount, new BigDecimal("1000.00")));

    assertThat(getCashAccount(party).getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getCashAccount(party).getEntries().size()).isEqualTo(2);

    assertThat(getServiceAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getServiceAccount().getEntries().size()).isEqualTo(2);
  }

  @Test
  public void findByExternalReference_shouldReturnTransactionsWithMatchingExternalReference() {
    User user = sampleUser().build();
    var party = ledgerPartyRepository.findByOwnerId(user.getPersonalCode());
    var cashAccount = getCashAccount(party);
    var serviceAccount = getServiceAccount();

    UUID externalReference1 = UUID.randomUUID();
    UUID externalReference2 = UUID.randomUUID();

    ledgerTransactionService.createTransaction(
        Instant.now(clock),
        Map.of("operationType", "PAYMENT", "externalReference", externalReference1.toString()),
        new LedgerEntryDto(cashAccount, new BigDecimal("100.00")),
        new LedgerEntryDto(serviceAccount, new BigDecimal("-100.00")));

    ledgerTransactionService.createTransaction(
        Instant.now(clock),
        Map.of("operationType", "PAYMENT", "externalReference", externalReference2.toString()),
        new LedgerEntryDto(cashAccount, new BigDecimal("200.00")),
        new LedgerEntryDto(serviceAccount, new BigDecimal("-200.00")));

    ledgerTransactionService.createTransaction(
        Instant.now(clock),
        Map.of("operationType", "OTHER_TRANSACTION"),
        new LedgerEntryDto(cashAccount, new BigDecimal("300.00")),
        new LedgerEntryDto(serviceAccount, new BigDecimal("-300.00")));

    var allTransactions = ledgerTransactionRepository.findAll();
    var transactionsWithRef1 =
        findByExternalReference(allTransactions, externalReference1.toString());
    assertThat(transactionsWithRef1)
        .singleElement()
        .satisfies(
            transaction ->
                assertThat(transaction.getMetadata().get("externalReference"))
                    .isEqualTo(externalReference1.toString()));

    var transactionsWithRef2 =
        findByExternalReference(allTransactions, externalReference2.toString());
    assertThat(transactionsWithRef2)
        .singleElement()
        .satisfies(
            transaction ->
                assertThat(transaction.getMetadata().get("externalReference"))
                    .isEqualTo(externalReference2.toString()));

    var transactionsWithNonExistentRef =
        findByExternalReference(allTransactions, UUID.randomUUID().toString());
    assertThat(transactionsWithNonExistentRef).isEmpty();
  }

  private List<LedgerTransaction> findByExternalReference(
      Iterable<LedgerTransaction> transactions, String externalReference) {
    List<LedgerTransaction> result = new ArrayList<>();
    for (LedgerTransaction transaction : transactions) {
      Object ref = transaction.getMetadata().get("externalReference");
      if (ref != null && externalReference.equals(ref.toString())) {
        result.add(transaction);
      }
    }
    return result;
  }
}
