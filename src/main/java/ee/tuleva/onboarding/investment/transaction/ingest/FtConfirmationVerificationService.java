package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_EXECUTION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static java.util.Comparator.comparing;

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
    Optional<TransactionOrder> orderOpt = findOrder(confirmation);
    if (orderOpt.isEmpty()) {
      log.warn(
          "No order found for FT confirmation: fund={}, isin={}, tradeDate={}",
          confirmation.fund(),
          confirmation.isin(),
          confirmation.tradeDate());
      return Optional.empty();
    }
    TransactionOrder order = orderOpt.get();

    Map<String, String> details = new LinkedHashMap<>();
    details.put("orderUuid", order.getOrderUuid().toString());

    FtVerificationStatus quantityStatus = checkQuantity(confirmation, order, details);
    FtVerificationStatus priceStatus = checkPrice(confirmation, details);

    FtConfirmationResult result =
        new FtConfirmationResult(quantityStatus, priceStatus, Map.copyOf(details));
    auditRecorder.recordVerified(order, confirmation, result);
    log.info(
        "FT confirmation verified: orderUuid={}, isin={}, tradeDate={}, quantityStatus={}, priceStatus={}",
        order.getOrderUuid(),
        confirmation.isin(),
        confirmation.tradeDate(),
        quantityStatus,
        priceStatus);
    return Optional.of(result);
  }

  private Optional<TransactionOrder> findOrder(FtConfirmation confirmation) {
    return orderRepository.findByInstrumentIsin(confirmation.isin()).stream()
        .filter(order -> order.getFund() == confirmation.fund())
        .filter(order -> hasTradeDate(order, confirmation.tradeDate()))
        .min(comparing(TransactionOrder::getId));
  }

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
