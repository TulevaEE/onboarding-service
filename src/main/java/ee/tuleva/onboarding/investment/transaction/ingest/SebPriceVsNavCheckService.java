package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SebPriceVsNavCheckService {

  private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.01");
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final PositionPriceResolver positionPriceResolver;
  private final PriceValidator priceValidator;
  private final InstrumentTypeClassifier classifier;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Value("${investment.transaction.nav-check.tolerance:0.01}")
  private BigDecimal tolerance = DEFAULT_TOLERANCE;

  public void check(TransactionExecution execution, TransactionOrder order) {
    if (!classifier.isEtf(order)) {
      log.debug(
          "Skipping price check for non-ETF instrument: executionId={}, isin={}, instrumentType={}",
          execution.getId(),
          order.getInstrumentIsin(),
          order.getInstrumentType());
      return;
    }
    if (execution.getExecutionTimestamp() == null) {
      log.warn(
          "Skipping price check, execution has no timestamp: executionId={}, isin={}",
          execution.getId(),
          order.getInstrumentIsin());
      return;
    }

    String isin = order.getInstrumentIsin();
    LocalDate tradeDate = execution.getExecutionTimestamp().atZone(TALLINN).toLocalDate();

    Optional<BigDecimal> marketPriceOpt =
        positionPriceResolver
            .resolve(isin, tradeDate, clock.instant())
            .filter(resolved -> resolved.validationStatus() == OK)
            .filter(resolved -> tradeDate.equals(resolved.priceDate()))
            .map(ResolvedPrice::usedPrice);

    if (marketPriceOpt.isEmpty()) {
      log.info(
          "No same-date market price for ETF execution: executionId={}, isin={}, tradeDate={}",
          execution.getId(),
          isin,
          tradeDate);
      eventPublisher.publishEvent(new NavMissingEvent(execution.getId(), isin, tradeDate));
      return;
    }

    BigDecimal marketPrice = marketPriceOpt.get();
    BigDecimal execPrice = execution.getUnitPrice();
    if (priceValidator.isWithinTolerance(execPrice, marketPrice, tolerance)) {
      log.debug(
          "ETF execution within tolerance: executionId={}, isin={}, execPrice={}, marketPrice={}",
          execution.getId(),
          isin,
          execPrice,
          marketPrice);
      return;
    }

    BigDecimal deltaPercent = priceValidator.computeDeltaPercent(execPrice, marketPrice);
    log.warn(
        "ETF execution price diverges from market price: executionId={}, isin={}, execPrice={}, marketPrice={}, deltaPercent={}",
        execution.getId(),
        isin,
        execPrice,
        marketPrice,
        deltaPercent);
    eventPublisher.publishEvent(
        new ExecutionMismatchEvent(
            execution.getId(), isin, execPrice, marketPrice, deltaPercent, tradeDate));
  }
}
