package ee.tuleva.onboarding.investment.transaction;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.investment.transaction.CommandOutcome.StoppedByInputGates;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
class TransactionCommandRunner {

  private final TransactionCommandRepository commandRepository;
  private final TransactionAuditEventRepository auditEventRepository;
  private final TransactionPreparationService preparationService;
  private final TradePrepInputGateNotifier inputGateNotifier;
  private final EntityManager entityManager;

  TransactionCommandResponse createAndProcess(
      CreateTransactionCommandRequest request, String actor) {
    return createAndProcessEach(List.of(request), actor).getFirst();
  }

  List<TransactionCommandResponse> createAndProcessAll(
      @Nullable List<TulevaFund> funds,
      TransactionMode mode,
      LocalDate asOfDate,
      String actor,
      Map<TulevaFund, CashOverride> cashOverrides) {
    List<TulevaFund> targetFunds =
        funds == null || funds.isEmpty() ? List.of(TulevaFund.values()) : funds;
    if (!targetFunds.containsAll(cashOverrides.keySet())) {
      throw new ResponseStatusException(
          BAD_REQUEST,
          "Cash override for fund not in batch: funds="
              + targetFunds
              + ", cashFunds="
              + cashOverrides.keySet());
    }
    return createAndProcessEach(
        targetFunds.stream()
            .map(
                fund ->
                    new CreateTransactionCommandRequest(
                        fund,
                        mode,
                        asOfDate,
                        null,
                        cashOverrides.get(fund),
                        null,
                        null,
                        null,
                        null))
            .toList(),
        actor);
  }

  private List<TransactionCommandResponse> createAndProcessEach(
      List<CreateTransactionCommandRequest> requests, String actor) {
    List<StoppedByInputGates> stops = new ArrayList<>();
    try {
      return requests.stream().map(request -> createAndProcessOne(request, actor, stops)).toList();
    } finally {
      inputGateNotifier.notifyStopped(stops);
    }
  }

  private TransactionCommandResponse createAndProcessOne(
      CreateTransactionCommandRequest request, String actor, List<StoppedByInputGates> stops) {
    forgetEntitiesAnEarlierCommandOfThisRequestLoaded();
    TransactionCommand command = TransactionCommand.requested(request, actor);
    commandRepository.save(command);
    log.info(
        "Admin transaction command created: id={}, fund={}, mode={}, asOfDate={}",
        command.getId(),
        command.getFund(),
        command.getMode(),
        command.getAsOfDate());
    CommandOutcome outcome = preparationService.processCommand(command);
    if (outcome instanceof StoppedByInputGates stop) {
      stops.add(stop);
    }
    return TransactionCommandResponse.from(command, outcome.orders(), snapshotOf(command));
  }

  private void forgetEntitiesAnEarlierCommandOfThisRequestLoaded() {
    entityManager.clear();
  }

  @Nullable
  private Map<String, Object> snapshotOf(TransactionCommand command) {
    Long batchId = command.getBatchId();
    return batchId == null
        ? null
        : TransactionAuditEvent.calculationSnapshotIn(
            auditEventRepository.findByBatchIdOrderByCreatedAt(batchId));
  }
}
