package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.IGNORED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.NOTHING_TO_CANCEL;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_EXECUTION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_REPLACEMENT;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.REPLACED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.VOIDED;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.matchesQuantity;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.withinQuantityTolerance;
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

  private static final BigDecimal DEFAULT_PRICE_TOLERANCE = new BigDecimal("0.001");
  private static final int EXECUTION_PENDING_BUSINESS_DAYS = 1;
  private static final int REPLACEMENT_PENDING_BUSINESS_DAYS = 1;
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
        outcomes.add(new FtConfirmationOutcome(confirmation, unchecked(error)));
      }
    }
    digest.publish(outcomes);
    return results;
  }

  private FtConfirmationResult verifyAndRecord(
      FtConfirmation confirmation, FtCorrections corrections, String actor) {
    Computed computed = compute(confirmation, corrections);
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

  private Computed compute(FtConfirmation confirmation, FtCorrections corrections) {
    FtConfirmationResult ignored = ignoredResult(confirmation);
    if (ignored != null) {
      return new Computed(ignored, null);
    }

    List<TransactionOrder> candidates = candidateOrders(confirmation);
    if (confirmation.isCancellation()) {
      return cancellation(confirmation, candidates, corrections);
    }
    if (corrections.isVoided(confirmation)) {
      return voided(confirmation, candidates);
    }
    List<TransactionOrder> liveOrders = liveOrders(candidates);
    if (liveOrders.isEmpty() || matchesOnlyAWithdrawnOrder(confirmation, candidates, liveOrders)) {
      log.warn(
          "No order found for FT confirmation: fund={}, isin={}, tradeDate={}",
          confirmation.fund(),
          confirmation.isin(),
          confirmation.tradeDate());
      return new Computed(orphan(), null);
    }

    FtOrderSelection selection = FtOrderSelection.among(liveOrders, confirmation);
    TransactionOrder order = selection.order();

    Map<String, String> details = new LinkedHashMap<>();
    details.put("orderUuid", order.getOrderUuid().toString());

    if (selection.ambiguous()) {
      details.put("ambiguousOrderCount", String.valueOf(selection.matchCount()));
      return new Computed(result(AMBIGUOUS, AMBIGUOUS, details), order);
    }

    FtVerificationStatus quantityStatus = checkQuantity(confirmation, order, details);
    FtVerificationStatus priceStatus = checkPrice(confirmation, details);
    return new Computed(result(quantityStatus, priceStatus, details), order);
  }

  private record Computed(FtConfirmationResult result, @Nullable TransactionOrder order) {}

  private Computed voided(FtConfirmation confirmation, List<TransactionOrder> candidates) {
    Map<String, String> details = cancellationDetails(confirmation);
    @Nullable TransactionOrder order =
        candidates.isEmpty()
            ? null
            : linked(FtOrderSelection.among(candidates, confirmation), details);
    return new Computed(result(VOIDED, VOIDED, details), order);
  }

  private Computed cancellation(
      FtConfirmation confirmation, List<TransactionOrder> candidates, FtCorrections corrections) {
    Map<String, String> details = cancellationDetails(confirmation);
    List<TransactionOrder> liveOrders = liveOrders(candidates);
    if (liveOrders.isEmpty()) {
      return new Computed(result(NOTHING_TO_CANCEL, NOTHING_TO_CANCEL, details), null);
    }
    boolean ofAWithdrawnOrder = matchesOnlyAWithdrawnOrder(confirmation, candidates, liveOrders);
    FtOrderSelection selection = FtOrderSelection.among(liveOrders, confirmation);
    @Nullable TransactionOrder order = ofAWithdrawnOrder ? null : linked(selection, details);
    if (corrections.newConfirmationsCoverEveryOrder(confirmation, liveOrders)) {
      FtVerificationStatus status = ofAWithdrawnOrder ? NOTHING_TO_CANCEL : REPLACED;
      return new Computed(result(status, status, details), order);
    }
    LocalDate replacementDeadline = replacementDeadline(confirmation);
    if (!LocalDate.now(clock).isAfter(replacementDeadline)) {
      details.put("replacementPendingUntil", replacementDeadline.toString());
      return new Computed(result(PENDING_REPLACEMENT, PENDING_REPLACEMENT, details), order);
    }
    if (selection.matchCount() > 1) {
      details.put("ambiguousOrderCount", String.valueOf(selection.matchCount()));
      return new Computed(result(AMBIGUOUS, AMBIGUOUS, details), order);
    }
    details.put("replacementMissing", "no new confirmation by " + replacementDeadline);
    return new Computed(result(CANCELLED, CANCELLED, details), order);
  }

  private static List<TransactionOrder> liveOrders(List<TransactionOrder> candidates) {
    return candidates.stream().filter(order -> !order.isCancelledOrDiscarded()).toList();
  }

  private static boolean matchesOnlyAWithdrawnOrder(
      FtConfirmation confirmation,
      List<TransactionOrder> candidates,
      List<TransactionOrder> liveOrders) {
    return liveOrders.stream().noneMatch(order -> matchesQuantity(order, confirmation))
        && candidates.stream().anyMatch(order -> matchesQuantity(order, confirmation));
  }

  private LocalDate replacementDeadline(FtConfirmation cancellation) {
    LocalDate firstSeen = auditRecorder.firstSeenOn(cancellation);
    LocalDate waitFrom =
        firstSeen.isAfter(cancellation.tradeDate()) ? firstSeen : cancellation.tradeDate();
    return target2Calendar.addBusinessDays(waitFrom, REPLACEMENT_PENDING_BUSINESS_DAYS);
  }

  private static Map<String, String> cancellationDetails(FtConfirmation confirmation) {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("cancellationSignature", confirmation.cancellationSignature());
    return details;
  }

  private static TransactionOrder linked(FtOrderSelection selection, Map<String, String> details) {
    TransactionOrder order = selection.order();
    details.put("orderUuid", order.getOrderUuid().toString());
    return order;
  }

  private static FtConfirmationResult result(
      FtVerificationStatus quantityStatus,
      FtVerificationStatus priceStatus,
      Map<String, String> details) {
    return new FtConfirmationResult(quantityStatus, priceStatus, Map.copyOf(details));
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

    BigDecimal reference = referencePrice.get();
    details.put("referencePrice", reference.toPlainString());
    if (reference.signum() <= 0) {
      details.put("invalidReferencePrice", "true");
      return ERROR;
    }
    if (priceValidator.isWithinTolerance(confirmation.grossPrice(), reference, priceTolerance)) {
      return OK;
    }
    details.put(
        "priceDeltaPercent",
        priceValidator.computeDeltaPercent(confirmation.grossPrice(), reference).toPlainString());
    return ERROR;
  }
}
