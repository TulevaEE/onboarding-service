package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.IGNORED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_EXECUTION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@NullMarked
@Slf4j
@Service
@RequiredArgsConstructor
public class FtConfirmationVerificationService {

  private static final BigDecimal QUANTITY_TOLERANCE = BigDecimal.ONE;
  private static final BigDecimal DEFAULT_PRICE_TOLERANCE = new BigDecimal("0.001");
  private static final int EXECUTION_PENDING_BUSINESS_DAYS = 1;
  private static final String ADMIN_ACTOR = "admin";

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final PositionPriceResolver positionPriceResolver;
  private final PriceValidator priceValidator;
  private final Target2Calendar target2Calendar;
  private final FtConfirmationAuditRecorder auditRecorder;
  private final FtConfirmationDigest digest;
  private final Clock clock;

  @Value("${investment.transaction.ft-confirmation.price-tolerance:0.001}")
  private BigDecimal priceTolerance = DEFAULT_PRICE_TOLERANCE;

  public FtConfirmationResult verify(FtConfirmation confirmation) {
    FtConfirmationResult result = verifyAndRecord(confirmation, ADMIN_ACTOR);
    digest.publish(List.of(new FtConfirmationOutcome(confirmation, result)));
    return result;
  }

  public List<FtConfirmationBatchResult> verifyAll(
      List<FtConfirmation> confirmations, String actor) {
    List<FtConfirmationBatchResult> results = new ArrayList<>();
    List<FtConfirmationOutcome> outcomes = new ArrayList<>();
    for (int index = 0; index < confirmations.size(); index++) {
      FtConfirmation confirmation = confirmations.get(index);
      if (confirmation == null) {
        results.add(FtConfirmationBatchResult.failed(index, null, "null confirmation row"));
        continue;
      }
      try {
        FtConfirmationResult result = verifyAndRecord(confirmation, actor);
        results.add(FtConfirmationBatchResult.verified(index, confirmation.isin(), result));
        outcomes.add(new FtConfirmationOutcome(confirmation, result));
      } catch (RuntimeException e) {
        log.error(
            "FT confirmation row failed: index={}, isin={}, error={}",
            index,
            confirmation.isin(),
            e.getMessage(),
            e);
        results.add(FtConfirmationBatchResult.failed(index, confirmation.isin(), e.getMessage()));
      }
    }
    digest.publish(outcomes);
    return results;
  }

  private FtConfirmationResult verifyAndRecord(FtConfirmation confirmation, String actor) {
    Computed computed = compute(confirmation);
    record(confirmation, computed, actor);
    return computed.result();
  }

  private void record(FtConfirmation confirmation, Computed computed, String actor) {
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

  private Computed compute(FtConfirmation confirmation) {
    FtConfirmationResult ignored = ignoredResult(confirmation);
    if (ignored != null) {
      return new Computed(ignored, null);
    }

    List<TransactionOrder> candidates = candidateOrders(confirmation);
    if (candidates.isEmpty()) {
      log.warn(
          "No order found for FT confirmation: fund={}, isin={}, tradeDate={}",
          confirmation.fund(),
          confirmation.isin(),
          confirmation.tradeDate());
      return new Computed(orphan(), null);
    }

    OrderSelection selection = selectOrder(confirmation, candidates);
    TransactionOrder order = selection.order();

    Map<String, String> details = new LinkedHashMap<>();
    details.put("orderUuid", order.getOrderUuid().toString());

    if (confirmation.isCancellation()) {
      if (selection.matchCount() > 1) {
        details.put("ambiguousOrderCount", String.valueOf(selection.matchCount()));
        return new Computed(result(AMBIGUOUS, AMBIGUOUS, details), order);
      }
      details.put("cancellationSignature", confirmation.cancellationSignature());
      return new Computed(result(CANCELLED, CANCELLED, details), order);
    }

    if (selection.ambiguous()) {
      details.put("ambiguousOrderCount", String.valueOf(selection.matchCount()));
      return new Computed(result(AMBIGUOUS, AMBIGUOUS, details), order);
    }

    FtVerificationStatus quantityStatus = checkQuantity(confirmation, order, details);
    FtVerificationStatus priceStatus = checkPrice(confirmation, details);
    return new Computed(result(quantityStatus, priceStatus, details), order);
  }

  private record Computed(FtConfirmationResult result, @Nullable TransactionOrder order) {}

  private static FtConfirmationResult result(
      FtVerificationStatus quantityStatus,
      FtVerificationStatus priceStatus,
      Map<String, String> details) {
    return new FtConfirmationResult(quantityStatus, priceStatus, Map.copyOf(details));
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

  private OrderSelection selectOrder(
      FtConfirmation confirmation, List<TransactionOrder> candidates) {
    if (candidates.size() == 1) {
      return new OrderSelection(candidates.get(0), false, 1);
    }
    List<TransactionOrder> quantityMatches =
        candidates.stream()
            .filter(order -> order.getOrderQuantity() != null)
            .filter(
                order -> withinQuantityTolerance(confirmation.quantity(), order.getOrderQuantity()))
            .collect(toList());
    if (quantityMatches.size() == 1) {
      return new OrderSelection(quantityMatches.get(0), false, 1);
    }
    if (quantityMatches.size() > 1) {
      return new OrderSelection(oldest(quantityMatches), true, quantityMatches.size());
    }
    return new OrderSelection(oldest(candidates), false, candidates.size());
  }

  private static TransactionOrder oldest(List<TransactionOrder> orders) {
    return orders.stream().min(comparing(TransactionOrder::getId)).orElseThrow();
  }

  private record OrderSelection(TransactionOrder order, boolean ambiguous, int matchCount) {}

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

  private static boolean withinQuantityTolerance(BigDecimal ftQuantity, BigDecimal quantity) {
    return ftQuantity.subtract(quantity).abs().compareTo(QUANTITY_TOLERANCE) <= 0;
  }

  private FtVerificationStatus checkPrice(
      FtConfirmation confirmation, Map<String, String> details) {
    Optional<BigDecimal> referencePrice =
        positionPriceResolver
            .resolve(confirmation.isin(), confirmation.tradeDate())
            .filter(resolved -> resolved.validationStatus() == ValidationStatus.OK)
            .filter(resolved -> confirmation.tradeDate().equals(resolved.priceDate()))
            .map(ResolvedPrice::usedPrice);

    if (referencePrice.isEmpty()) {
      return PENDING_NAV;
    }

    details.put("referencePrice", referencePrice.get().toPlainString());
    if (priceValidator.isWithinTolerance(
        confirmation.grossPrice(), referencePrice.get(), priceTolerance)) {
      return OK;
    }
    details.put(
        "priceDeltaPercent",
        priceValidator
            .computeDeltaPercent(confirmation.grossPrice(), referencePrice.get())
            .toPlainString());
    return ERROR;
  }
}
