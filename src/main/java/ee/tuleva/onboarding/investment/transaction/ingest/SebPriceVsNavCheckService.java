package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
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

  private final NavLookup navLookup;
  private final PriceValidator priceValidator;
  private final InstrumentTypeClassifier classifier;
  private final ApplicationEventPublisher eventPublisher;

  @Value("${investment.transaction.nav-check.tolerance:0.01}")
  private BigDecimal tolerance = DEFAULT_TOLERANCE;

  public void check(TransactionExecution execution, TransactionOrder order) {
    if (!classifier.isEtf(order)) {
      log.debug(
          "Skipping NAV check for non-ETF instrument: executionId={}, isin={}, instrumentType={}",
          execution.getId(),
          order.getInstrumentIsin(),
          order.getInstrumentType());
      return;
    }
    if (execution.getExecutionTimestamp() == null) {
      log.warn(
          "Skipping NAV check, execution has no timestamp: executionId={}, isin={}",
          execution.getId(),
          order.getInstrumentIsin());
      return;
    }

    String isin = order.getInstrumentIsin();
    LocalDate tradeDate = execution.getExecutionTimestamp().atZone(TALLINN).toLocalDate();
    Optional<BigDecimal> navPriceOpt = navLookup.findMarketPrice(isin, tradeDate);

    if (navPriceOpt.isEmpty()) {
      log.info(
          "NAV row missing for ETF execution: executionId={}, isin={}, tradeDate={}",
          execution.getId(),
          isin,
          tradeDate);
      eventPublisher.publishEvent(new NavMissingEvent(execution.getId(), isin, tradeDate));
      return;
    }

    BigDecimal navPrice = navPriceOpt.get();
    BigDecimal execPrice = execution.getUnitPrice();
    if (priceValidator.isWithinTolerance(execPrice, navPrice, tolerance)) {
      log.debug(
          "ETF execution within tolerance: executionId={}, isin={}, execPrice={}, navPrice={}",
          execution.getId(),
          isin,
          execPrice,
          navPrice);
      return;
    }

    BigDecimal deltaPercent = priceValidator.computeDeltaPercent(execPrice, navPrice);
    log.warn(
        "ETF execution price diverges from NAV: executionId={}, isin={}, execPrice={}, navPrice={}, deltaPercent={}",
        execution.getId(),
        isin,
        execPrice,
        navPrice,
        deltaPercent);
    eventPublisher.publishEvent(
        new ExecutionMismatchEvent(
            execution.getId(), isin, execPrice, navPrice, deltaPercent, tradeDate));
  }
}
