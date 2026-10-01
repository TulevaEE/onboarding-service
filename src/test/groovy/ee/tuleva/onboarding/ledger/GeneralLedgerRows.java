package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY_REVERSAL;
import static java.util.Comparator.comparingInt;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.simple.JdbcClient;

@TestComponent
public class GeneralLedgerRows {

  private final JdbcClient jdbcClient;
  private final LedgerTransactionRepository transactionRepository;

  GeneralLedgerRows(JdbcClient jdbcClient, LedgerTransactionRepository transactionRepository) {
    this.jdbcClient = jdbcClient;
    this.transactionRepository = transactionRepository;
  }

  public record JournalEntryVersion(
      UUID id, Instant transactionDate, int revision, String documentType, boolean live) {}

  public BigDecimal balanceAtEndOf(String entity, String code, LocalDate date) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entry e
            JOIN ledger.account a ON a.id = e.account_id
            JOIN ledger.transaction t ON t.id = e.transaction_id
            WHERE a.name = :name AND t.transaction_date <= :cutoff
            """)
        .param("name", "GENERAL_LEDGER:" + entity + ":" + code)
        .param("cutoff", Timestamp.from(JournalEntryPart.endOfBookingDay(date)))
        .query(BigDecimal.class)
        .single()
        .setScale(2);
  }

  public long count(TransactionType transactionType) {
    return jdbcClient
        .sql(
            "SELECT count(*) FROM ledger.transaction"
                + " WHERE transaction_type = CAST(:type AS ledger.transaction_type)")
        .param("type", transactionType.name())
        .query(Long.class)
        .single();
  }

  public List<JournalEntryVersion> versionsOf(String entity, String source, String sourceKey) {
    return jdbcClient
        .sql(
            """
            SELECT t.id,
                   (SELECT count(*) FROM ledger.transaction r
                    WHERE r.transaction_type = 'JOURNAL_ENTRY_REVERSAL'
                      AND r.external_reference = t.id) AS reversals
            FROM ledger.transaction t
            WHERE t.transaction_type = 'JOURNAL_ENTRY' AND t.external_reference = :reference
            """)
        .param("reference", JournalEntryWriter.reference(entity, source, sourceKey))
        .query((rs, rowNum) -> version(rs.getObject("id", UUID.class), rs.getLong("reversals")))
        .list()
        .stream()
        .sorted(comparingInt(JournalEntryVersion::revision))
        .toList();
  }

  private JournalEntryVersion version(UUID id, long reversals) {
    var journalEntry = transactionRepository.findById(id).orElseThrow();
    return new JournalEntryVersion(
        id,
        journalEntry.getTransactionDate(),
        ((Number) journalEntry.getMetadata().get("revision")).intValue(),
        (String) journalEntry.getMetadata().get("documentType"),
        reversals == 0);
  }

  Optional<Instant> reversalDateOf(UUID journalEntryId) {
    return transactionRepository
        .findByExternalReferenceAndTransactionType(journalEntryId, JOURNAL_ENTRY_REVERSAL)
        .map(LedgerTransaction::getTransactionDate);
  }

  public void deleteAll() {
    jdbcClient
        .sql(
            """
            DELETE FROM ledger.entry WHERE transaction_id IN (
              SELECT id FROM ledger.transaction
              WHERE transaction_type IN ('JOURNAL_ENTRY', 'JOURNAL_ENTRY_REVERSAL'))
            """)
        .update();
    jdbcClient
        .sql(
            """
            DELETE FROM ledger.transaction
            WHERE transaction_type IN ('JOURNAL_ENTRY', 'JOURNAL_ENTRY_REVERSAL')
            """)
        .update();
    jdbcClient
        .sql("DELETE FROM ledger.account WHERE name LIKE 'GENERAL\\_LEDGER:%' ESCAPE '\\'")
        .update();
  }
}
