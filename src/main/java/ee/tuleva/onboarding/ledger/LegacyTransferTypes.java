package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
class LegacyTransferTypes {

  private final LedgerTransactionRepository ledgerTransactionRepository;

  List<UnitHoldingChange> resolved(List<UnitHoldingChange> history) {
    List<UUID> unnamed =
        history.stream()
            .filter(LegacyTransferTypes::namesNoOperation)
            .map(UnitHoldingChange::transactionId)
            .toList();
    if (unnamed.isEmpty()) {
      return history;
    }

    Map<UUID, LedgerTransaction> transactions = findAll(unnamed);
    return history.stream()
        .map(
            change ->
                namesNoOperation(change)
                    ? change.recordingOperation(operationOf(change.transactionId(), transactions))
                    : change)
        .toList();
  }

  private static boolean namesNoOperation(UnitHoldingChange change) {
    return change.transactionType() == TRANSFER;
  }

  private Map<UUID, LedgerTransaction> findAll(List<UUID> transactionIds) {
    return ledgerTransactionRepository.findAllById(transactionIds).stream()
        .collect(Collectors.toMap(LedgerTransaction::getId, Function.identity()));
  }

  private static TransactionType operationOf(
      UUID transactionId, Map<UUID, LedgerTransaction> transactions) {
    return Optional.ofNullable(transactions.get(transactionId))
        .flatMap(LedgerTransaction::findOperationType)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Fund units moved under a transaction that names no operation to replay their"
                        + " cost from: transactionId="
                        + transactionId));
  }
}
