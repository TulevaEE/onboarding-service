package ee.tuleva.onboarding.savings.fund.report;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@Import(TrusteeReportRepository.class)
@Transactional
class TrusteeReportRepositoryTest {

  @Autowired private TrusteeReportRepository repository;
  @Autowired private JdbcClient jdbcClient;

  private UUID fundUnitsAccountId;
  private UUID subscriptionsAccountId;
  private UUID redemptionsAccountId;

  @BeforeEach
  void setUp() {
    fundUnitsAccountId = createSystemAccount(FUND_UNITS_OUTSTANDING.getAccountName(), "FUND_UNIT");
    subscriptionsAccountId = createUserAccount("SUBSCRIPTIONS", "EUR");
    redemptionsAccountId = createUserAccount("REDEMPTIONS", "EUR");
  }

  @Test
  void returnsEmptyListWhenNoData() {
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void aggregatesDailyUnitActivity() {
    createFundUnitEntry(Instant.parse("2026-02-03T10:00:00Z"), new BigDecimal("100.00000"));
    insertNav("EE0000003283", LocalDate.of(2026, 2, 2), new BigDecimal("1.0050"));

    var result = repository.findAll();

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                TrusteeReportRow.builder()
                    .reportDate(LocalDate.of(2026, 2, 3))
                    .nav(new BigDecimal("1.005"))
                    .issuedUnits(new BigDecimal("100"))
                    .issuedAmount(ZERO)
                    .redeemedUnits(ZERO)
                    .redeemedAmount(ZERO)
                    .totalOutstandingUnits(new BigDecimal("100"))
                    .build()));
  }

  @Test
  void aggregatesIssuedAndRedeemedUnitsAndAmounts() {
    var day1 = Instant.parse("2026-02-03T10:00:00Z");
    var day2 = Instant.parse("2026-02-04T10:00:00Z");

    createFundUnitEntry(day1, new BigDecimal("500.00000"));
    createEurEntry(day1, subscriptionsAccountId, new BigDecimal("-500.00"));

    createFundUnitEntry(day2, new BigDecimal("-50.00000"));
    createEurEntry(day2, redemptionsAccountId, new BigDecimal("49.75"));

    insertNav("EE0000003283", LocalDate.of(2026, 2, 2), new BigDecimal("1.0000"));
    insertNav("EE0000003283", LocalDate.of(2026, 2, 3), new BigDecimal("0.9950"));

    var result = repository.findAll();

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                TrusteeReportRow.builder()
                    .reportDate(LocalDate.of(2026, 2, 4))
                    .nav(new BigDecimal("0.995"))
                    .issuedUnits(ZERO)
                    .issuedAmount(ZERO)
                    .redeemedUnits(new BigDecimal("50"))
                    .redeemedAmount(new BigDecimal("49.75"))
                    .totalOutstandingUnits(new BigDecimal("450"))
                    .build(),
                TrusteeReportRow.builder()
                    .reportDate(LocalDate.of(2026, 2, 3))
                    .nav(new BigDecimal("1.0"))
                    .issuedUnits(new BigDecimal("500"))
                    .issuedAmount(new BigDecimal("500"))
                    .redeemedUnits(ZERO)
                    .redeemedAmount(ZERO)
                    .totalOutstandingUnits(new BigDecimal("500"))
                    .build()));
  }

  private UUID createSystemAccount(String name, String assetType) {
    var id = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.account (id, name, purpose, account_type, asset_type)
            VALUES (:id, :name,
              CAST('SYSTEM_ACCOUNT' AS ledger.account_purpose),
              CAST('LIABILITY' AS ledger.account_type),
              CAST(:assetType AS ledger.asset_type))
            """)
        .param("id", id)
        .param("name", name)
        .param("assetType", assetType)
        .update();
    return id;
  }

  private UUID createUserAccount(String name, String assetType) {
    var partyId = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.party (id, party_type, owner_id, details)
            VALUES (:id, CAST('USER' AS ledger.party_type), :ownerId, '{}')
            """)
        .param("id", partyId)
        .param("ownerId", UUID.randomUUID().toString())
        .update();

    var id = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.account (id, name, purpose, account_type, owner_party_id, asset_type)
            VALUES (:id, :name,
              CAST('USER_ACCOUNT' AS ledger.account_purpose),
              CAST('LIABILITY' AS ledger.account_type),
              :partyId,
              CAST(:assetType AS ledger.asset_type))
            """)
        .param("id", id)
        .param("name", name)
        .param("partyId", partyId)
        .param("assetType", assetType)
        .update();
    return id;
  }

  private void createFundUnitEntry(Instant date, BigDecimal amount) {
    var transactionId = insertTransaction(date);
    insertEntry(transactionId, fundUnitsAccountId, amount, "FUND_UNIT");
  }

  private void createEurEntry(Instant date, UUID accountId, BigDecimal amount) {
    var transactionId = insertTransaction(date);
    insertEntry(transactionId, accountId, amount, "EUR");
  }

  private UUID insertTransaction(Instant date) {
    var transactionId = UUID.randomUUID();
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.transaction (id, transaction_type, transaction_date, metadata)
            VALUES (:id, CAST('FUND_SUBSCRIPTION' AS ledger.transaction_type), :date, '{}')
            """)
        .param("id", transactionId)
        .param("date", Timestamp.from(date))
        .update();
    return transactionId;
  }

  private void insertEntry(
      UUID transactionId, UUID accountId, BigDecimal amount, String assetType) {
    jdbcClient
        .sql(
            """
            INSERT INTO ledger.entry (id, account_id, transaction_id, amount, asset_type)
            VALUES (:id, :accountId, :transactionId, :amount, CAST(:assetType AS ledger.asset_type))
            """)
        .param("id", UUID.randomUUID())
        .param("accountId", accountId)
        .param("transactionId", transactionId)
        .param("amount", amount)
        .param("assetType", assetType)
        .update();
  }

  private void insertNav(String isin, LocalDate date, BigDecimal value) {
    jdbcClient
        .sql(
            """
            INSERT INTO index_values (key, date, value, provider, updated_at)
            VALUES (:key, :date, :value, 'TEST', CURRENT_TIMESTAMP)
            """)
        .param("key", isin)
        .param("date", date)
        .param("value", value)
        .update();
  }
}
