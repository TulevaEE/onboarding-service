package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.ledger.UserAccount.CASH;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class LedgerTransactionMetadataTest {

  @Autowired private LedgerService ledgerService;
  @Autowired private LedgerTransactionRepository ledgerTransactionRepository;
  @Autowired private LedgerAccountRepository ledgerAccountRepository;
  @Autowired private LedgerPartyRepository ledgerPartyRepository;
  @Autowired private LedgerTransactionService ledgerTransactionService;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private Clock clock;

  @AfterEach
  void cleanup() {
    ledgerTransactionRepository.deleteAll();
    ledgerAccountRepository.deleteAll();
    ledgerPartyRepository.deleteAll();
  }

  @Test
  void metadata_serializesLocalDateAsIsoString() {
    var user = sampleUser().build();
    var cashAccount = ledgerService.getPartyAccount(user.getPersonalCode(), PERSON, CASH);
    var systemAccount = ledgerService.getSystemAccount(INCOMING_PAYMENTS_CLEARING, TKF100);

    var metadata =
        Map.<String, Object>of(
            "accrualDate", LocalDate.of(2026, 3, 7),
            "someInstant", Instant.parse("2026-03-07T10:00:00Z"),
            "someString", "hello",
            "amount", new BigDecimal("1000000.00").stripTrailingZeros());

    var transaction =
        ledgerTransactionService.createTransaction(
            ADJUSTMENT,
            Instant.now(clock),
            UUID.randomUUID(),
            metadata,
            new LedgerEntryDto(cashAccount, new BigDecimal("100.00")),
            new LedgerEntryDto(systemAccount, new BigDecimal("-100.00")));

    String rawJson =
        jdbcClient
            .sql("SELECT CAST(metadata AS VARCHAR) FROM ledger.transaction WHERE id = :id")
            .param("id", transaction.getId())
            .query(String.class)
            .single();

    assertThat(rawJson).contains("\"2026-03-07\"");
    assertThat(rawJson).doesNotContain("[2026,3,7]");
    assertThat(rawJson).contains("1000000");
    assertThat(rawJson).doesNotContain("1E+");
  }
}
