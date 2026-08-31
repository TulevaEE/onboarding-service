package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.conversion.PendingExchange;
import java.math.BigDecimal;

class ExchangeAdapter implements PendingExchange {

  private final Exchange exchange;

  ExchangeAdapter(Exchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public Integer getPillar() {
    return exchange.getPillar();
  }

  @Override
  public boolean isFromOwnFund() {
    return exchange.isFromOwnFund();
  }

  @Override
  public boolean isToOwnFund() {
    return exchange.isToOwnFund();
  }

  @Override
  public boolean isFullAmount() {
    return exchange.isFullAmount();
  }

  @Override
  public boolean isFullAmount(BigDecimal fundBalanceUnits) {
    return exchange.isFullAmount(fundBalanceUnits);
  }

  @Override
  public String getSourceIsin() {
    return exchange.getSourceIsin();
  }

  @Override
  public String getTargetIsin() {
    return exchange.getTargetIsin();
  }

  @Override
  public BigDecimal getSourceFundFees() {
    return exchange.getSourceFundFees();
  }

  @Override
  public BigDecimal getTargetFundFees() {
    return exchange.getTargetFundFees();
  }

  @Override
  public BigDecimal getValue(BigDecimal totalValue, BigDecimal totalUnits) {
    return exchange.getValue(totalValue, totalUnits);
  }

  @Override
  public boolean isToPik() {
    return exchange.isToPik();
  }
}
