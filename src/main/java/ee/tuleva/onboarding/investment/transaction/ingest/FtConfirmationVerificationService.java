package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.IGNORED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_EXECUTION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.liveOrders;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.matchesOnlyAWithdrawnOrder;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.withinQuantityTolerance;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationBatchResult;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@NullMarked
@Slf4j
@Service
@RequiredArgsConstructor
public class FtConfirmationVerificationService {

  private static final int EXECUTION_PENDING_BUSINESS_DAYS = 1;
  private static final String ADMIN_ACTOR = "admin";

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final FtPriceCheck priceCheck;
  private final Target2Calendar target2Calendar;
  private final FtConfirmationAuditRecorder auditRecorder;
  private final FtCancellationCheck cancellationCheck;
  private final FtConfirmationDigest digest;
  private final Clock clock;

  public FtConfirmationResult verify(FtConfirmation confirmation) {
    FtConfirmationResult result =
        verifyAndRecord(confirmation, FtCorrections.among(List.of(confirmation)), ADMIN_ACTOR);
    digest.publish(List.of(new FtConfirmationOutcome(confirmation, result)));
    return result;
  }

  public List<FtConfirmationBatchResult> verifyAll(
      List<FtConfirmation> confirmations, String actor) {
    List<FtConfirmationBatchResult> results = new ArrayList<>();
    List<FtConfirmationOutcome> outcomes = new ArrayList<>();
    FtCorrections corrections = FtCorrections.among(confirmations);
    for (int index = 0; index < confirmations.size(); index++) {
      FtConfirmation confirmation = confirmations.get(index);
      if (confirmation == null) {
        results.add(FtConfirmationBatchResult.failed(index, null, "null confirmation row"));
        continue;
      }
      try {
        FtConfirmationResult result = verifyAndRecord(confirmation, corrections, actor);
        results.add(FtConfirmationBatchResult.verified(index, confirmation.isin(), result));
        outcomes.add(new FtConfirmationOutcome(confirmation, result));
      } catch (RuntimeException e) {
        log.error(
            "FT confirmation row failed: index={}, isin={}, error={}",
            index,
            confirmation.isin(),
            e.getMessage(),
            e);
        String error = e.getMessage() == null ? e.toString() : e.getMessage();
        results.add(FtConfirmationBatchResult.failed(index, confirmation.isin(), error));
        if (hasEveryTradeField(confirmation)) {
          outcomes.add(new FtConfirmationOutcome(confirmation, unchecked(error)));
        }
      }
    }
    digest.publish(outcomes);
    return results;
  }

  private FtConfirmationResult verifyAndRecord(
      FtConfirmation confirmation, FtCorrections corrections, String actor) {
    FtVerification computed = compute(confirmation, corrections);
    record(confirmation, computed, actor);
    return computed.result();
  }

  private void record(FtConfirmation confirmation, FtVerification computed, String actor) {
    if (computed.result().quantityStatus() == IGNORED) {
      return;
    }
    boolean recorded =
        auditRecorder.recordOutcome(computed.order(), confirmation, computed.result(), actor);
    if (recorded) {
      log.info(
          "FT confirmation verified: isin={}, tradeDate={}, type={}, quantityStatus={}, priceStatus={}",
          confirmation.isin(),
          confirmation.tradeDate(),
          confirmation.type(),
          computed.result().quantityStatus(),
          computed.result().priceStatus());
    }
  }

  private FtVerification compute(FtConfirmation confirmation, FtCorrections corrections) {
    FtConfirmationResult ignored = ignoredResult(confirmation);
    if (ignored != null) {
      return new FtVerification(ignored, null);
    }

    List<TransactionOrder> candidates = candidateOrders(confirmation);
    if (confirmation.isCancellation()) {
      return cancellationCheck.cancellation(confirmation, candidates, corrections);
    }
    if (corrections.isVoided(confirmation)) {
      return cancellationCheck.voided(confirmation, candidates);
    }
    List<TransactionOrder> liveOrders = liveOrders(candidates);
    if (liveOrders.isEmpty() || matchesOnlyAWithdrawnOrder(confirmation, candidates, liveOrders)) {
      log.warn(
          "No order found for FT confirmation: fund={}, isin={}, tradeDate={}",
          confirmation.fund(),
          confirmation.isin(),
          confirmation.tradeDate());
      return new FtVerification(orphan(), null);
    }

    FtOrderSelection selection = FtOrderSelection.among(liveOrders, confirmation);
    TransactionOrder order = selection.order();

    Map<String, String> details = new LinkedHashMap<>();
    details.put("orderUuid", order.getOrderUuid().toString());

    if (selection.ambiguous()) {
      details.put("ambiguousOrderCount", String.valueOf(selection.matchCount()));
      return new FtVerification(result(AMBIGUOUS, AMBIGUOUS, details), order);
    }

    FtVerificationStatus quantityStatus = checkQuantity(confirmation, order, details);
    FtVerificationStatus priceStatus =
        priceCheck.check(
            confirmation.isin(), confirmation.tradeDate(), confirmation.grossPrice(), details);
    return new FtVerification(result(quantityStatus, priceStatus, details), order);
  }

  private static FtConfirmationResult result(
      FtVerificationStatus quantityStatus,
      FtVerificationStatus priceStatus,
      Map<String, String> details) {
    return new FtConfirmationResult(quantityStatus, priceStatus, Map.copyOf(details));
  }

  private static boolean hasEveryTradeField(FtConfirmation confirmation) {
    return Stream.of(
            confirmation.fund(),
            confirmation.isin(),
            confirmation.tradeDate(),
            confirmation.quantity(),
            confirmation.grossPrice())
        .allMatch(Objects::nonNull);
  }

  private static FtConfirmationResult unchecked(String error) {
    return new FtConfirmationResult(UNCHECKED, UNCHECKED, Map.of("verificationError", error));
  }

  private static FtConfirmationResult orphan() {
    return new FtConfirmationResult(
        ORPHAN, ORPHAN, Map.of("orphanReason", "no matching order in registry"));
  }

  private @Nullable FtConfirmationResult ignoredResult(FtConfirmation confirmation) {
    if (confirmation.suppressed()) {
      return ignored(confirmation, "manually suppressed false positive");
    }
    if (isWeekend(confirmation.tradeDate())) {
      return ignored(confirmation, "weekend trade date: " + confirmation.tradeDate());
    }
    return null;
  }

  private FtConfirmationResult ignored(FtConfirmation confirmation, String reason) {
    log.info(
        "FT confirmation ignored: fund={}, isin={}, tradeDate={}, reason={}",
        confirmation.fund(),
        confirmation.isin(),
        confirmation.tradeDate(),
        reason);
    return new FtConfirmationResult(IGNORED, IGNORED, Map.of("ignoreReason", reason));
  }

  private static boolean isWeekend(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
  }

  private List<TransactionOrder> candidateOrders(FtConfirmation confirmation) {
    return orderRepository.findByInstrumentIsin(confirmation.isin()).stream()
        .filter(order -> order.getOrderVenue() == OrderVenue.FT)
        .filter(order -> order.getFund() == confirmation.fund())
        .filter(order -> hasTradeDate(order, confirmation.tradeDate()))
        .collect(toList());
  }

  private static boolean hasTradeDate(TransactionOrder order, LocalDate ftUtcTradeDate) {
    var timestamp = order.getOrderTimestamp();
    return timestamp != null
        && LocalDate.ofInstant(timestamp, ZoneOffset.UTC).equals(ftUtcTradeDate);
  }

  private FtVerificationStatus checkQuantity(
      FtConfirmation confirmation, TransactionOrder order, Map<String, String> details) {
    boolean orderQuantityOk = false;
    if (order.getOrderQuantity() == null) {
      details.put("orderQuantityMissing", "order has no quantity");
    } else {
      details.put("orderQuantity", order.getOrderQuantity().toPlainString());
      orderQuantityOk = withinQuantityTolerance(confirmation.quantity(), order.getOrderQuantity());
    }

    BigDecimal totalExecuted = sumExecutedQuantityAcrossPieces(order);
    Optional<BigDecimal> executedQuantity =
        totalExecuted.signum() > 0 ? Optional.of(totalExecuted) : Optional.empty();

    if (executedQuantity.isEmpty()) {
      LocalDate executionDeadline =
          target2Calendar.addBusinessDays(
              confirmation.tradeDate(), EXECUTION_PENDING_BUSINESS_DAYS);
      if (!LocalDate.now(clock).isAfter(executionDeadline)) {
        details.put("executionPendingUntil", executionDeadline.toString());
        return orderQuantityOk ? PENDING_EXECUTION : ERROR;
      }
      details.put("executionMissing", "no execution after " + executionDeadline);
      return ERROR;
    }

    details.put("executedQuantity", executedQuantity.get().toPlainString());
    boolean executedQuantityOk =
        withinQuantityTolerance(confirmation.quantity(), executedQuantity.get());
    return orderQuantityOk && executedQuantityOk ? OK : ERROR;
  }

  private BigDecimal sumExecutedQuantityAcrossPieces(TransactionOrder order) {
    return executionRepository.findAllByOrderId(order.getId()).stream()
        .map(TransactionExecution::getExecutedQuantity)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
