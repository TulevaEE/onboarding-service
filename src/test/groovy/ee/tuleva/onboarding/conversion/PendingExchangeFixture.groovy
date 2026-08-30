package ee.tuleva.onboarding.conversion

import java.math.RoundingMode

class PendingExchangeFixture implements PendingExchange {

  Integer pillar
  boolean fromOwnFund
  boolean toOwnFund
  String sourceIsin
  String targetIsin
  BigDecimal sourceFundFees
  BigDecimal targetFundFees
  BigDecimal amount
  boolean toPik

  @Override
  Integer getPillar() { pillar }

  @Override
  boolean isFromOwnFund() { fromOwnFund }

  @Override
  boolean isToOwnFund() { toOwnFund }

  @Override
  boolean isFullAmount() { amount.intValue() == 1 }

  @Override
  boolean isFullAmount(BigDecimal fundBalanceUnits) {
    amount.compareTo(fundBalanceUnits) == 0
  }

  @Override
  String getSourceIsin() { sourceIsin }

  @Override
  String getTargetIsin() { targetIsin }

  @Override
  BigDecimal getSourceFundFees() { sourceFundFees }

  @Override
  BigDecimal getTargetFundFees() { targetFundFees }

  @Override
  BigDecimal getValue(BigDecimal totalValue, BigDecimal totalUnits) {
    if (pillar == 2) {
      return amount.multiply(totalValue)
    }
    if (pillar == 3) {
      return BigDecimal.ZERO.compareTo(totalUnits) == 0
          ? BigDecimal.ZERO
          : amount.multiply(totalValue).divide(totalUnits, 2, RoundingMode.HALF_UP)
    }
    throw new IllegalStateException("Unknown pillar: " + pillar)
  }

  @Override
  boolean isToPik() { toPik }
}
