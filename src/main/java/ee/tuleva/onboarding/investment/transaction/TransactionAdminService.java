package ee.tuleva.onboarding.investment.transaction;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
class TransactionAdminService {

  private final TransactionCommandRepository commandRepository;
  private final TransactionBatchRepository batchRepository;
  private final TransactionOrderRepository orderRepository;
  private final TransactionPreparationService preparationService;
  private final Clock clock;

  TransactionCommandResponse createAndProcess(
      TulevaFund fund,
      TransactionMode mode,
      LocalDate asOfDate,
      @Nullable Map<String, Object> manualAdjustments) {
    TransactionCommand command =
        TransactionCommand.builder()
            .fund(fund)
            .mode(mode)
            .asOfDate(asOfDate)
            .manualAdjustments(manualAdjustments == null ? Map.of() : manualAdjustments)
            .status(CommandStatus.PROCESSING)
            .build();
    commandRepository.save(command);
    log.info(
        "Admin transaction command created: id={}, fund={}, mode={}, asOfDate={}",
        command.getId(),
        fund,
        mode,
        asOfDate);
    ProcessCommandResult result = preparationService.processCommand(command);
    List<TransactionOrder> orders = result == null ? List.of() : result.orders();
    return TransactionCommandResponse.from(command, orders);
  }

  List<TransactionCommandResponse> createAndProcessAll(
      @Nullable List<TulevaFund> funds, TransactionMode mode, LocalDate asOfDate) {
    List<TulevaFund> targetFunds =
        funds == null || funds.isEmpty() ? List.of(TulevaFund.values()) : funds;
    return targetFunds.stream().map(fund -> createAndProcess(fund, mode, asOfDate, null)).toList();
  }

  Optional<TransactionCommandResponse> getCommand(Long id) {
    return commandRepository
        .findById(id)
        .map(command -> TransactionCommandResponse.from(command, ordersOf(command)));
  }

  Optional<TransactionBatchResponse> getBatch(Long id) {
    return batchRepository
        .findById(id)
        .map(
            batch ->
                TransactionBatchResponse.from(batch, orderRepository.findByBatchId(batch.getId())));
  }

  TransactionBatchResponse confirmAndFinalize(Long id) {
    TransactionBatch batch =
        batchRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        NOT_FOUND, "Transaction batch not found: id=" + id));
    if (batch.getStatus() != BatchStatus.AWAITING_CONFIRMATION) {
      throw new ResponseStatusException(
          CONFLICT, "Batch not awaiting confirmation: id=" + id + ", status=" + batch.getStatus());
    }
    log.info("Admin confirmed transaction batch: id={}", id);
    batch.setStatus(BatchStatus.CONFIRMED);
    preparationService.finalizeConfirmedBatch(batch);
    return TransactionBatchResponse.from(batch, orderRepository.findByBatchId(batch.getId()));
  }

  Optional<byte[]> exportFile(Long id, String type) {
    if (!TransactionBatchResponse.EXPORT_TYPES.contains(type)) {
      return Optional.empty();
    }
    return batchRepository
        .findById(id)
        .flatMap(batch -> Optional.ofNullable(batch.getMetadata().get(type)))
        .filter(String.class::isInstance)
        .map(export -> Base64.getDecoder().decode((String) export));
  }

  TransactionDailySummary dailySummary() {
    LocalDate today = LocalDate.now(clock);
    List<TransactionDailySummary.FundSummary> funds =
        Arrays.stream(TulevaFund.values()).map(fund -> fundSummary(fund, today)).toList();
    return new TransactionDailySummary(today, funds);
  }

  private TransactionDailySummary.FundSummary fundSummary(TulevaFund fund, LocalDate today) {
    List<TransactionOrder> unsettledOrders = orderRepository.findUnsettledOrders(fund, today);
    Optional<TransactionBatch> latestBatch =
        batchRepository.findFirstByFundOrderByCreatedAtDesc(fund);
    return new TransactionDailySummary.FundSummary(
        fund,
        unsettledOrders.size(),
        unsettledOrders.stream()
            .map(TransactionOrder::getOrderAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        latestBatch.map(TransactionBatch::getId).orElse(null),
        latestBatch.map(TransactionBatch::getStatus).orElse(null),
        latestBatch.map(TransactionBatch::getCreatedAt).orElse(null));
  }

  private List<TransactionOrder> ordersOf(TransactionCommand command) {
    return command.getBatchId() == null
        ? List.of()
        : orderRepository.findByBatchId(command.getBatchId());
  }
}
