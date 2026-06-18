package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_EXECUTION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@NullMarked
@Slf4j
@Service
@RequiredArgsConstructor
public class FtConfirmationVerificationService {

  private static final BigDecimal QUANTITY_TOLERANCE = BigDecimal.ONE;
  private static final BigDecimal DEFAULT_PRICE_TOLERANCE = new BigDecimal("0.001");
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final int EXECUTION_PENDING_BUSINESS_DAYS = 1;

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final PositionPriceResolver positionPriceResolver;
  private final PriceValidator priceValidator;
  private final Target2Calendar target2Calendar;
  private final FtConfirmationAuditRecorder auditRecorder;
  private final Clock clock;

  @Value("${investment.transaction.ft-confirmation.price-tolerance:0.001}")
  private BigDecimal priceTolerance = DEFAULT_PRICE_TOLERANCE;

  public Optional<FtConfirmationResult> verify(FtConfirmation confirmation) {
    List<TransactionOrder> candidates = candidateOrders(confirmation);
    if (candidates.isEmpty()) {
      log.warn(
          "No order found for FT confirmation: fund={}, isin={}, tradeDate={}",
          confirmation.fund(),
          confirmation.isin(),
          confirmation.tradeDate());
      return Optional.empty();
    }

    OrderSelection selection = selectOrder(confirmation, candidates);
    TransactionOrder order = selection.order();

    Map<String, String> details = new LinkedHashMap<>();
    details.put("orderUuid", order.getOrderUuid().toString());

    if (confirmation.isCancellation()) {
      return cancel(confirmation, order, details);
    }

    if (selection.ambiguous()) {
      details.put("ambiguousOrderCount", String.valueOf(selection.matchCount()));
      return result(confirmation, order, AMBIGUOUS, AMBIGUOUS, details);
    }

    FtVerificationStatus quantityStatus = checkQuantity(confirmation, order, details);
    FtVerificationStatus priceStatus = checkPrice(confirmation, details);
    return result(confirmation, order, quantityStatus, priceStatus, details);
  }

  private Optional<FtConfirmationResult> cancel(
      FtConfirmation confirmation, TransactionOrder order, Map<String, String> details) {
    details.put("cancellationSignature", confirmation.cancellationSignature());
    return result(confirmation, order, CANCELLED, CANCELLED, details);
  }

  private Optional<FtConfirmationResult> result(
      FtConfirmation confirmation,
      TransactionOrder order,
      FtVerificationStatus quantityStatus,
      FtVerificationStatus priceStatus,
      Map<String, String> details) {
    FtConfirmationResult result =
        new FtConfirmationResult(quantityStatus, priceStatus, Map.copyOf(details));
    auditRecorder.recordVerified(order, confirmation, result);
    log.info(
        "FT confirmation verified: orderUuid={}, isin={}, tradeDate={}, type={}, quantityStatus={}, priceStatus={}",
        order.getOrderUuid(),
        confirmation.isin(),
        confirmation.tradeDate(),
        confirmation.type(),
        quantityStatus,
        priceStatus);
    return Optional.of(result);
  }

  private List<TransactionOrder> candidateOrders(FtConfirmation confirmation) {
    return orderRepository.findByInstrumentIsin(confirmation.isin()).stream()
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

  private static boolean hasTradeDate(TransactionOrder order, LocalDate tradeDate) {
    return order.getOrderTimestamp() != null
        && order.getOrderTimestamp().atZone(TALLINN).toLocalDate().equals(tradeDate);
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

    Optional<BigDecimal> executedQuantity =
        executionRepository
            .findByOrderId(order.getId())
            .map(TransactionExecution::getExecutedQuantity)
            .filter(quantity -> quantity.signum() > 0);

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
