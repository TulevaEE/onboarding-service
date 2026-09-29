package ee.tuleva.onboarding.banking.message;

import ee.tuleva.onboarding.banking.BankType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface BankingMessageRepository extends CrudRepository<BankingMessage, UUID> {

  List<BankingMessage> findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc();

  @Query(
      """
      select new ee.tuleva.onboarding.banking.message.StoredStatement(m.statementFrom, m.statementTo, m.processedAt)
      from BankingMessage m
      where m.bankType = :bankType
        and m.messageType = :messageType
        and m.accountIban = :accountIban
        and m.statementFrom <= :to
        and m.statementTo >= :from
      """)
  List<StoredStatement> findStatements(
      BankType bankType,
      BankMessageType messageType,
      String accountIban,
      LocalDate from,
      LocalDate to);

  @Query(
      """
      select m
      from BankingMessage m
      where m.accountIban = :accountIban
        and m.processedAt is not null
        and m.failedAt is null
      order by m.statementTo desc, m.receivedAt desc
      limit 1
      """)
  Optional<BankingMessage> findLatestProcessedStatement(String accountIban);

  @Query(
      """
      select min(m.statementFrom)
      from BankingMessage m
      where m.bankType = :bankType
        and m.messageType = :messageType
        and m.accountIban = :accountIban
      """)
  Optional<LocalDate> findEarliestStatementDate(
      BankType bankType, BankMessageType messageType, String accountIban);
}
