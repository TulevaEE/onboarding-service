package ee.tuleva.onboarding.investment.transaction;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
class TransactionAdminService {

  private final TransactionCommandRepository commandRepository;
  private final TransactionBatchRepository batchRepository;
  private final TransactionOrderRepository orderRepository;
  private final TransactionAuditEventRepository auditEventRepository;
  private final TransactionPreparationService preparationService;
  private final Clock clock;

  TransactionCommandResponse createAndProcess(
      TulevaFund fund,
      TransactionMode mode,
      LocalDate asOfDate,
      @Nullable Map<String, Object> manualAdjustments,
      String actor,
      @Nullable BigDecimal cash) {
    TransactionCommand command =
        TransactionCommand.builder()
            .fund(fund)
            .mode(mode)
            .asOfDate(asOfDate)
            .manualAdjustments(manualAdjustments == null ? Map.of() : manualAdjustments)
            .cash(cash)
            .actor(actor)
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
    return TransactionCommandResponse.from(command, orders, snapshotOf(command));
  }

  @Nullable
  private Map<String, Object> snapshotOf(TransactionCommand command) {
    return command.getBatchId() == null ? null : calculationSnapshot(command.getBatchId());
  }

  List<TransactionCommandResponse> createAndProcessAll(
      @Nullable List<TulevaFund> funds,
      TransactionMode mode,
      LocalDate asOfDate,
      String actor,
      Map<TulevaFund, BigDecimal> cashOverrides) {
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
    return targetFunds.stream()
        .map(fund -> createAndProcess(fund, mode, asOfDate, null, actor, cashOverrides.get(fund)))
        .toList();
  }

  Optional<TransactionCommandResponse> getCommand(Long id) {
    return commandRepository
        .findById(id)
        .map(
            command ->
                TransactionCommandResponse.from(command, ordersOf(command), snapshotOf(command)));
  }

  Optional<TransactionBatchResponse> getBatch(Long id) {
    return batchRepository
        .findById(id)
        .map(
            batch ->
                TransactionBatchResponse.from(
                    batch,
                    orderRepository.findByBatchId(batch.getId()),
                    calculationSnapshot(batch.getId())));
  }

  @Nullable
  private Map<String, Object> calculationSnapshot(Long batchId) {
    return auditEventRepository.findByBatchIdOrderByCreatedAt(batchId).stream()
        .filter(event -> "CALCULATION_COMPLETED".equals(event.getEventType()))
        .map(TransactionAuditEvent::getPayload)
        .findFirst()
        .orElse(null);
  }

  @Transactional
  TransactionBatchResponse confirmAndFinalize(Long id, String actor) {
    TransactionBatch batch =
        batchRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        NOT_FOUND, "Transaction batch not found: id=" + id));
    if (batch.getStatus() != BatchStatus.DRAFT) {
      throw new ResponseStatusException(
          CONFLICT, "Batch not in DRAFT status: id=" + id + ", status=" + batch.getStatus());
    }
    log.info("Admin confirmed transaction batch: id={}, actor={}", id, actor);
    batch.setStatus(BatchStatus.CONFIRMED);
    batch.setConfirmedBy(actor);
    batch.setConfirmedAt(Instant.now(clock));
    try {
      batchRepository.saveAndFlush(batch);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ResponseStatusException(CONFLICT, "Batch was modified concurrently: id=" + id);
    }
    preparationService.finalizeConfirmedBatch(batch);
    return TransactionBatchResponse.from(
        batch, orderRepository.findByBatchId(batch.getId()), calculationSnapshot(batch.getId()));
  }

  @Transactional
  TransactionBatchResponse cancelBatch(Long id, String reason, String actor) {
    TransactionBatch batch =
        batchRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        NOT_FOUND, "Transaction batch not found: id=" + id));
    if (batch.getStatus() != BatchStatus.SENT) {
      throw new ResponseStatusException(
          CONFLICT,
          "Only SENT batches can be cancelled: id=" + id + ", status=" + batch.getStatus());
    }
    List<TransactionOrder> orders = orderRepository.findByBatchId(batch.getId());
    if (orders.stream().anyMatch(TransactionAdminService::isInMarket)) {
      throw new ResponseStatusException(
          CONFLICT,
          "Batch has orders past SENT and cannot be cancelled: id="
              + id
              + ", status="
              + batch.getStatus());
    }
    Instant now = Instant.now(clock);
    log.info("Admin cancelled transaction batch: id={}, actor={}, reason={}", id, actor, reason);
    batch.setStatus(BatchStatus.CANCELLED);
    batch.setCancellationReason(reason);
    batch.setCancelledBy(actor);
    batch.setCancelledAt(now);
    batchRepository.save(batch);

    orders.stream()
        .filter(order -> order.getOrderStatus() != OrderStatus.CANCELLED)
        .forEach(order -> order.setOrderStatus(OrderStatus.CANCELLED));
    orderRepository.saveAll(orders);

    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .batch(batch)
            .eventType("BATCH_CANCELLED")
            .actor(actor)
            .createdAt(now)
            .payload(Map.of("reason", reason, "actor", actor, "orderCount", orders.size()))
            .build());

    return TransactionBatchResponse.from(batch, orders, calculationSnapshot(batch.getId()));
  }

  @Transactional
  TransactionBatchResponse discardBatch(Long id, String actor) {
    TransactionBatch batch =
        batchRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        NOT_FOUND, "Transaction batch not found: id=" + id));
    if (batch.getStatus() != BatchStatus.DRAFT) {
      throw new ResponseStatusException(
          CONFLICT, "Batch not in DRAFT status: id=" + id + ", status=" + batch.getStatus());
    }
    Instant now = Instant.now(clock);
    log.info("Admin discarded transaction batch: id={}, actor={}", id, actor);
    batch.setStatus(BatchStatus.DISCARDED);
    try {
      batchRepository.saveAndFlush(batch);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ResponseStatusException(CONFLICT, "Batch was modified concurrently: id=" + id);
    }

    List<TransactionOrder> orders = orderRepository.findByBatchId(batch.getId());
    orders.forEach(order -> order.setOrderStatus(OrderStatus.DISCARDED));
    orderRepository.saveAll(orders);

    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .batch(batch)
            .eventType("BATCH_DISCARDED")
            .actor(actor)
            .createdAt(now)
            .payload(Map.of("actor", actor, "orderCount", orders.size()))
            .build());

    return TransactionBatchResponse.from(batch, orders, calculationSnapshot(batch.getId()));
  }

  @Transactional
  TransactionOrderResponse cancelOrder(Long orderId, String reason, String actor) {
    TransactionOrder order =
        orderRepository
            .findById(orderId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        NOT_FOUND, "Transaction order not found: id=" + orderId));
    if (order.getOrderStatus() != OrderStatus.SENT) {
      throw new ResponseStatusException(
          CONFLICT,
          "Only SENT orders can be cancelled: id="
              + orderId
              + ", status="
              + order.getOrderStatus());
    }
    Instant now = Instant.now(clock);
    log.info(
        "Admin cancelled transaction order: id={}, actor={}, reason={}", orderId, actor, reason);
    order.setOrderStatus(OrderStatus.CANCELLED);
    orderRepository.save(order);

    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .orderId(order.getId())
            .eventType("ORDER_CANCELLED")
            .actor(actor)
            .createdAt(now)
            .payload(Map.of("reason", reason, "actor", actor))
            .build());

    return TransactionOrderResponse.from(order);
  }

  private static boolean isInMarket(TransactionOrder order) {
    return order.getOrderStatus() == OrderStatus.EXECUTED
        || order.getOrderStatus() == OrderStatus.SETTLED;
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
