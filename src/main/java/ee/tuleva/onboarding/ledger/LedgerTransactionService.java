package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TEST;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("dev")
@Service
@RequiredArgsConstructor
class LedgerTransactionService {
  private final Clock clock;

  private final LedgerTransactionRepository ledgerTransactionRepository;
  private final LedgerEntryService ledgerEntryService;

  @Transactional
  LedgerTransaction createTransaction(String description, List<LedgerEntryDto> ledgerEntryDtos) {
    var transaction =
        LedgerTransaction.builder()
            .description(description)
            .transactionTypeId(TEST) // TODO correct type
            .transactionDate(clock.instant())
            .metadata(Map.of())
            .eventLogId(1) // TODO event log ID
            .build();

    ledgerTransactionRepository.save(transaction);

    for (LedgerEntryDto ledgerEntryDto : ledgerEntryDtos) {
      ledgerEntryService.createEntry(ledgerEntryDto.account, transaction, ledgerEntryDto.amount);
    }

    return transaction;
  }

  record LedgerEntryDto(LedgerAccount account, BigDecimal amount) {}
}
