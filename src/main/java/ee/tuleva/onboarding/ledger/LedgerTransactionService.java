package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile({"dev", "test"})
@Service
@RequiredArgsConstructor
public class LedgerTransactionService {
  private final Clock clock;

  private final LedgerTransactionRepository ledgerTransactionRepository;
  private final LedgerEntryService ledgerEntryService;

  @Transactional
  public LedgerTransaction createTransaction(
      TransactionType type, Map<String, Object> metadata, LedgerEntryDto... ledgerEntryDtos) {
    var transaction =
        LedgerTransaction.builder()
            .description("") // TODO remove this field
            .transactionTypeId(type)
            .transactionDate(clock.instant())
            .metadata(metadata)
            // .eventLogId(1) // TODO event log ID
            .build();

    ledgerTransactionRepository.save(transaction);

    for (LedgerEntryDto ledgerEntryDto : ledgerEntryDtos) {
      ledgerEntryService.createEntry(ledgerEntryDto.account, transaction, ledgerEntryDto.amount);
    }

    return transaction;
  }

  public record LedgerEntryDto(LedgerAccount account, BigDecimal amount) {}
}
